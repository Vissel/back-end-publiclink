package com.qrpublic.apartment.saleenv.impl;

import com.qrpublic.apartment.adapter.authentication.request.FindUserAuthenRequest;
import com.qrpublic.apartment.adapter.authentication.response.FindUserAuthenResponse;
import com.qrpublic.apartment.adapter.user.request.UserAuthenTokenRequest;
import com.qrpublic.apartment.adapter.user.request.UserUserAuthRequest;
import com.qrpublic.apartment.adapter.user.response.UserAuthTokenResponse;
import com.qrpublic.apartment.constant.CommonConstant;
import com.qrpublic.apartment.constant.LinkConstant;
import com.qrpublic.apartment.core.linkBuilder.LinkBuilder;
import com.qrpublic.apartment.core.model.LinkModel;
import com.qrpublic.apartment.core.model.SaleEnvironmentModel;
import com.qrpublic.apartment.core.model.SellerModel;
import com.qrpublic.apartment.core.service.CoreEnvironmentService;
import com.qrpublic.apartment.core.service.CoreProductService;
import com.qrpublic.apartment.core.service.CoreRequestService;
import com.qrpublic.apartment.core.service.CoreUserService;
import com.qrpublic.apartment.entity.Pricing;
import com.qrpublic.apartment.entity.Request;
import com.qrpublic.apartment.entity.SaleEnvironment;
import com.qrpublic.apartment.exception.ResourceNotFoundException;
import com.qrpublic.apartment.integration.OperatedSellerClient;
import com.qrpublic.apartment.integration.OperatedUserClient;
import com.qrpublic.apartment.integration.SecurityCheckClient;
import com.qrpublic.apartment.model.SellerDTO;
import com.qrpublic.apartment.model.UserType;
import com.qrpublic.apartment.product.request.CreateProductRequest;
import com.qrpublic.apartment.repository.PricingRepository;
import com.qrpublic.apartment.repository.SaleEnvironmentRepository;
import com.qrpublic.apartment.requestmodel.Pagination;
import com.qrpublic.apartment.requestmodel.RequestDTO;
import com.qrpublic.apartment.requestmodel.SaleEnvDTO;
import com.qrpublic.apartment.saleenv.SaleEnvironmentService;
import com.qrpublic.apartment.saleenv.convertor.SaleEnvConvertor;
import com.qrpublic.apartment.saleenv.request.CreateEnvironmentRequest;
import com.qrpublic.apartment.saleenv.request.ListEnvironmentRequest;
import com.qrpublic.apartment.saleenv.response.ListEnvironmentResponse;
import com.qrpublic.apartment.service.LinkService;
import com.qrpublic.apartment.template.model.Result;
import com.qrpublic.apartment.template.service.PublicLinkServiceTemplate;
import com.qrpublic.apartment.util.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@PreAuthorize("hasRole('Admin')")
public class SaleEnvironmentServiceImpl implements SaleEnvironmentService {

    private static final long FIFTEEN_MINUTES = 15 * 60 * 1000L;
    private static final long FIVE_MINUTES = 5 * 60 * 1000L;
    @Autowired
    private SaleEnvironmentRepository repo;

    @Autowired
    CoreRequestService coreRequestService;
    @Autowired
    CoreEnvironmentService coreEnvironmentService;

    @Autowired
    LinkService linkService;

    @Autowired
    PublicLinkServiceTemplate publicLinkServiceTemplate;

    @Autowired
    CoreUserService coreUserService;

    @Autowired
    OperatedUserClient operatedUserClient;

    @Autowired
    OperatedSellerClient operatedSellerClient;

    @Autowired
    SecurityCheckClient securityCheckClient;

    @Autowired
    PricingRepository pricingRepository;
    @Autowired
    private CoreProductService coreProductService;
    @Autowired
    private NamedParameterJdbcOperations namedParameterJdbcOperations;

    @Override
    public SaleEnvironment createSaleEnvironment(Request request) {
        log.info("Creating sale environment.");
        SaleEnvironment env = repo.save(new SaleEnvironment(request));
        if (env.getEnvId() != null && !env.getEnvId().isBlank()) {
            final String publicLink = "";
            // generatePublicLink(env, request);
            log.info("Creating sale environment. Public link:{}", publicLink);
            env.setPublicLink(publicLink);
        }
        log.info("Creating sale environment. Update DB and done.{}", CommonConstant.END);
        return repo.save(env);
    }

    /**
     * Microservice design - from 2026
     * Create a sale environment with reactive error handling
     *
     * @param request the create environment request containing seller details and
     *                request ID
     * @return Mono of CreateEnvironmentResponse with error handling
     */
    @Override
    public SaleEnvDTO createSaleEnvironment(CreateEnvironmentRequest request) {
        log.info("{} Creating sale environment for request ID: {}", CommonConstant.START, request.getRequestUuid());
        RequestDTO requestDTO = convertToRequestDTO(request);

        CompletableFuture<SaleEnvironment> environmentFuture = CompletableFuture
                .supplyAsync(() -> createSaleEnvironment(requestDTO));
        CompletableFuture<SellerModel> sellerFuture = CompletableFuture
                .supplyAsync(() -> createOrGetSeller(requestDTO));

        SaleEnvironment environment = environmentFuture.join();
        SellerModel sellerModel = sellerFuture.join();

        // Persist product data if present
        CreateProductRequest productRequest = request.getProductRequest();
        if (productRequest != null) {
            Request requestEntity = environment.getRequest();
            coreProductService.saveProduct(productRequest, requestEntity);
        }

        final String authenToken = sellerModel.getSellerLinkModel() != null
                ? sellerModel.getSellerLinkModel().getToken()
                : CommonConstant.EMPTY;
        sellerModel.getSellerLinkModel().setContextString(
                LinkBuilder.buildAuthenticationLink(requestDTO.getReqUUID(), authenToken));

        log.info("{} Creating sale environment for request ID: {}", CommonConstant.END, request.getRequestUuid());
        return convertToSaleEnvDto(environment, sellerModel.getSellerLinkModel());
    }

    private SaleEnvironment createSaleEnvironment(RequestDTO requestDTO) {
        Request requestEntity = coreRequestService.getRequestByUuid(requestDTO.getReqUUID()).orElseThrow(
                () -> new ResourceNotFoundException("Request not found with UUID: " + requestDTO.getReqUUID()));

        // Create SaleEnvironment
        SaleEnvironment env = new SaleEnvironment();
        env.setRequest(requestEntity);
        // Generate public link by username + requestID
        final LinkModel linkModel = generatePublicLink(requestDTO);
        final String publicLink = linkModel.getToken();
        log.debug("Generated public link: {}", publicLink);
        env.setPublicLink(publicLink);
        return repo.save(env);
    }

    private SellerModel createOrGetSeller(RequestDTO requestDTO) {
        String username = requestDTO.getUsername();
        SellerDTO sellerDTO = requestDTO.getSeller();

        return operatedSellerClient.findUserByUsername(new FindUserAuthenRequest(username))
                .filter(response -> response.getUserName() != null && !response.getUserName().isBlank())
                .flatMap(findUserAuthen -> {
                    boolean isExpiringSoon = findUserAuthen.getExpiredAt() != null
                            && findUserAuthen.getExpiredAt()
                                    .before(new java.util.Date(System.currentTimeMillis() + FIVE_MINUTES));
                    if (isExpiringSoon) {
                        // expiry < 5 min => generate new token with 15 min validity, then update
                        String sellerName = sellerDTO != null ? sellerDTO.getName() : null;
                        return generateToken(username, sellerName, FIFTEEN_MINUTES)
                                .flatMap(authLink -> operatedSellerClient
                                        .updateUserAuth(toUserAuthRequest(username, authLink))
                                        .flatMap(result -> {
                                            if (!result.isSuccess()) {
                                                return Mono.error(new RuntimeException(
                                                        "Failed to update user auth: " + result.getErrorMessage()));
                                            }
                                            log.info("Refreshed token for seller: {}", username);
                                            return Mono.just(toSellerModel(username, authLink));
                                        }));
                    }
                    // Update sellerName on Request if missing
                    coreRequestService.updateSellerNameIfMissing(requestDTO.getReqUUID(), username);
                    // expiry >= 5 min => use existing token
                    LinkModel existingLink = new LinkModel(findUserAuthen.getAuthenticationToken(),
                            findUserAuthen.getCreatedAt(), findUserAuthen.getExpiredAt());
                    return Mono.just(toSellerModel(username, existingLink));
                })
                .switchIfEmpty(Mono.defer(() -> {
                    String name = sellerDTO != null ? sellerDTO.getName() : null;
                    return generateToken(username, name, FIFTEEN_MINUTES)
                            .flatMap(authLink -> {
                                // Step 1: Create user auth
                                return operatedUserClient.createUserAuth(toUserAuthRequest(username, authLink))
                                        .flatMap(result -> {
                                            if (!result.isSuccess()) {
                                                return Mono.error(new RuntimeException(
                                                        "Failed to create user auth: " + result.getErrorMessage()));
                                            }
                                            // Step 2: Create seller in internal DB (compensating transaction)
                                            try {
                                                SellerDTO newSellerDTO = new SellerDTO();
                                                newSellerDTO.setUsername(username);
                                                newSellerDTO.setUserType(UserType.SELLER);
                                                if (sellerDTO != null)
                                                    newSellerDTO.setName(sellerDTO.getName());

                                                if (coreUserService.findSeller(newSellerDTO) == null) {
                                                    coreUserService.createNewUser(newSellerDTO);
                                                }
                                                // Update sellerName on Request if missing
                                                coreRequestService.updateSellerNameIfMissing(requestDTO.getReqUUID(),
                                                        username);
                                                log.info("Created new seller in internal DB: {}", username);
                                                return Mono.just(toSellerModel(username, authLink));
                                            } catch (Exception e) {
                                                // Compensating transaction: rollback user auth
                                                log.error(
                                                        "Failed to create seller in internal DB, rolling back user auth for: {}",
                                                        username, e);
                                                return operatedSellerClient
                                                        .invalidateUserAuth(new FindUserAuthenRequest(username))
                                                        .flatMap(invalidateResult -> {
                                                            if (invalidateResult.isSuccess()) {
                                                                log.info("Successfully rolled back user auth for: {}",
                                                                        username);
                                                            } else {
                                                                log.error(
                                                                        "Failed to rollback user auth for: {}. Manual cleanup required!",
                                                                        username);
                                                            }
                                                            return Mono.<SellerModel>error(new RuntimeException(
                                                                    "Failed to create seller. Rolled back user auth. Error: "
                                                                            + e.getMessage(),
                                                                    e));
                                                        })
                                                        .onErrorResume(rollbackError -> {
                                                            log.error(
                                                                    "Rollback also failed for: {}. Manual cleanup required!",
                                                                    username, rollbackError);
                                                            return Mono.<SellerModel>error(new RuntimeException(
                                                                    "Failed to create seller and rollback failed. Manual cleanup required!",
                                                                    e));
                                                        });
                                            }
                                        });
                            });
                }))
                .block();
    }

    private Mono<LinkModel> generateToken(String username, String name, long validTimeMillis) {
        UserAuthenTokenRequest tokenRequest = new UserAuthenTokenRequest();
        tokenRequest.setUsername(username);
        tokenRequest.setRole(UserType.SELLER.name());
        tokenRequest.setValidTime(validTimeMillis);
        tokenRequest.setName(name);
        return securityCheckClient.generateAuthenToken(tokenRequest).map(this::toAuthLinkModel);
    }

    private UserUserAuthRequest toUserAuthRequest(String username, LinkModel authLink) {
        UserUserAuthRequest request = new UserUserAuthRequest();
        request.setUserName(username);
        request.setAuthToken(authLink.getToken());
        request.setExpire(authLink.getExpire());
        return request;
    }

    private SellerModel toSellerModel(String username, LinkModel authLink) {
        SellerModel sellerModel = new SellerModel();
        sellerModel.setUsername(username);
        sellerModel.setSellerLinkModel(authLink);
        return sellerModel;
    }

    private LinkModel toAuthLinkModel(UserAuthTokenResponse tokenResponse) {
        return new LinkModel(tokenResponse.getAuthenticationToken(), tokenResponse.getIssueAt(),
                tokenResponse.getExpire());
    }

    private RequestDTO convertToRequestDTO(CreateEnvironmentRequest request) {
        RequestDTO requestDTO = new RequestDTO();
        requestDTO.setReqUUID(request.getRequestUuid());
        requestDTO.setUsername(request.getSellerRequest().getUsername());
        String name = request.getSellerRequest().getName();
        if (name != null && !name.isBlank()) {
            SellerDTO sellerDTO = new SellerDTO();
            sellerDTO.setUsername(request.getSellerRequest().getUsername());
            sellerDTO.setName(name);
            requestDTO.setSeller(sellerDTO);
        }
        return requestDTO;
    }

    private LinkModel generatePublicLink(RequestDTO requestDTO) {
        return linkService.generateSecureUrl(requestDTO.getReqUUID(),
                Map.of(LinkConstant.PARAM_USERNAME, requestDTO.getUsername()));
    }

    private LinkModel generateReqAuthLink(RequestDTO requestDTO) {
        LinkModel linkModel = linkService
                .generateAuthLink(Map.of(LinkConstant.PARAM_USERNAME, requestDTO.getUsername()));
        linkModel.setContextString(LinkBuilder.buildAuthenticationLink(requestDTO.getReqUUID(), linkModel.getToken()));
        return linkModel;
    }

    @Override
    public Mono<Result<ListEnvironmentResponse>> getEnvironments(
            Pagination<ListEnvironmentRequest> listEnvironmentRequestPagination) {
        return Mono.fromCallable(() -> {
            ListEnvironmentRequest filter = listEnvironmentRequestPagination.getListData() != null
                    && !listEnvironmentRequestPagination.getListData().isEmpty()
                            ? listEnvironmentRequestPagination.getListData().get(0)
                            : null;

            PageRequest pageable = PageRequest.of(listEnvironmentRequestPagination.getPage() - 1,
                    listEnvironmentRequestPagination.getSize(), Sort.by(Sort.Order.desc("createdAt")));

            if (filter != null) {
                return coreEnvironmentService.getEnvironments(pageable,
                        filter.getCreatedAt(),
                        filter.getCreatedBy(),
                        filter.getSellerName(),
                        filter.getRequestUuid());
            } else {
                return coreEnvironmentService.getEnvironments(pageable);
            }
        }).flatMap(resultPage -> {
            List<SaleEnvironmentModel> models = resultPage.getContent();
            List<String> usernames = models.stream()
                    .map(m -> m.getSeller() != null ? m.getSeller().getUsername() : null)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();

            if (usernames.isEmpty()) {
                return Mono.just(buildEnvironmentResult(models, resultPage.getTotalElements()));
            }

            return Flux.fromIterable(usernames)
                    .<SellerModel>flatMap(
                            username -> operatedSellerClient.findUserByUsername(new FindUserAuthenRequest(username))
                                    .map(this::convertToSellerModel)
                                    .filter(sm -> sm.getUsername() != null)
                                    .timeout(Duration.ofSeconds(3))
                                    .onErrorResume(error -> {
                                        log.error("Failed to fetch seller auth for [{}]: {}", username,
                                                error.getMessage());
                                        return Mono.empty();
                                    }))
                    .collectMap(SellerModel::getUsername)
                    .map(sellerMap -> {
                        List<SaleEnvironmentModel> hydratedModels = models.stream()
                                .map(model -> {
                                    if (model.getSeller() != null) {
                                        model.setSeller(sellerMap.getOrDefault(
                                                model.getSeller().getUsername(), model.getSeller()));
                                    }
                                    return model;
                                })
                                .toList();
                        return buildEnvironmentResult(hydratedModels, resultPage.getTotalElements());
                    });
        });
    }

    private Result<ListEnvironmentResponse> buildEnvironmentResult(List<SaleEnvironmentModel> models,
            long totalElements) {
        ListEnvironmentResponse response = new ListEnvironmentResponse();
        response.setTotal((int) totalElements);
        response.setListSaleEnv(models.stream()
                .map(SaleEnvConvertor::buildSaleEnvDTOFromModel)
                .toList());

        Result<ListEnvironmentResponse> resultWrapper = new Result<>();
        resultWrapper.setData(response);
        resultWrapper.setSuccess(true);
        return resultWrapper;
    }

    @Override
    public SaleEnvironment getEnvironmentByPublicLink(String publicLink) {
        return repo.findByPublicLink(publicLink).orElse(null);
    }

    @Override
    public String getPublicLinkBy(Request request) {
        return repo.findByRequest(request).get().getPublicLink();
    }

    @Override
    @PreAuthorize("permitAll()")
    public SaleEnvDTO getSaleEnvironmentByRequestUuid(String requestUuid) {
        SaleEnvironment env = repo.findByRequestReqUuid(requestUuid)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Sale environment not found for request UUID: " + requestUuid));
        return SaleEnvConvertor.buildEnvDTO(env).build();
    }

    @Override
    public Mono<SaleEnvDTO> getEnvironmentDetailByRequestUuid(String requestUuid) {
        SaleEnvironment env = repo.findByRequestReqUuid(requestUuid)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Sale environment not found for request UUID: " + requestUuid));

        List<Pricing> pricings = pricingRepository.findByRequest_ReqUUID(requestUuid);

        SaleEnvDTO environmentDetailResponse = SaleEnvConvertor.buildEnvDTO(env).build();
        environmentDetailResponse.setPricings(SaleEnvConvertor.toPricingDTOList(pricings));
        environmentDetailResponse.setTotalPrice(SaleEnvConvertor.calculateTotalPrice(pricings));
        environmentDetailResponse.setCurrency(!pricings.isEmpty() ? pricings.get(0).getCurrency() : "VND");

        // Fetch seller auth data from OperatedSellerClient
        String sellerName = env.getRequest() != null ? env.getRequest().getSellerName() : null;
        if (StringUtils.isNotBlank(sellerName)) {
            return operatedSellerClient
                    .findUserByUsername(new FindUserAuthenRequest(sellerName))
                    .map(authResponse -> {
                        if (authResponse.getAuthenticationToken() != null) {
                            String sellerAuthLink = LinkBuilder.buildAuthenticationLink(requestUuid,
                                    authResponse.getAuthenticationToken());
                            environmentDetailResponse.setSellerAuthLink(sellerAuthLink);
                            environmentDetailResponse.setSellerAuthLinkExpire(authResponse.getExpiredAt());
                        }
                        return environmentDetailResponse;
                    })
                    .onErrorResume(e -> {
                        log.warn("Failed to fetch seller auth data for username [{}]: {}", sellerName, e.getMessage());
                        return Mono.just(environmentDetailResponse);
                    });
        }

        return Mono.just(environmentDetailResponse);
    }

    private SellerModel convertToSellerModel(FindUserAuthenResponse response) {
        SellerModel sellerModel = new SellerModel();
        sellerModel.setUsername(response.getUserName());
        sellerModel.setName(response.getUserName());
        LinkModel sellerLinkModel = new LinkModel(response.getAuthenticationToken(),
                response.getExpiredAt(), response.getCreatedAt());
        sellerModel.setSellerLinkModel(sellerLinkModel);
        return sellerModel;
    }

    private SaleEnvDTO convertToSaleEnvDto(SaleEnvironment env, LinkModel linkModel) {
        return SaleEnvConvertor.buildEnvDTO(env)
                .sellerAuthLink(linkModel.getContextString())
                .sellerAuthLinkExpire(linkModel.getExpire())
                .createdBy(DateUtils.dateToString(linkModel.getIssueAt()))
                .build();
    }
}
