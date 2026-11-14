package com.qrpublic.apartment.saleenv.impl;

import com.qrpublic.apartment.adapter.authentication.request.FindUserAuthenRequest;
import com.qrpublic.apartment.adapter.authentication.response.FindUserAuthenResponse;
import com.qrpublic.apartment.adapter.user.request.ExtendAuthenTokenRequest;
import com.qrpublic.apartment.adapter.user.request.UserAuthenTokenRequest;
import com.qrpublic.apartment.adapter.user.request.UserUserAuthRequest;
import com.qrpublic.apartment.adapter.user.response.UserAuthTokenResponse;
import com.qrpublic.apartment.constant.CommonConstant;
import com.qrpublic.apartment.constant.LinkConstant;
import com.qrpublic.apartment.core.linkBuilder.LinkBuilder;
import com.qrpublic.apartment.core.model.*;
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
import com.qrpublic.apartment.requestmodel.PictureDTO;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@PreAuthorize("hasRole('Admin')")
public class SaleEnvironmentServiceImpl implements SaleEnvironmentService {

    private static final long FIVE_MINUTES = 5 * 60 * 1000L;
    private static final long ONE_MINUTE_SECONDS = 1 * 60 * 1000L;
    private static final long ONE_SECOND_MILI = 1 * 1000L;
    private static final long ONE_HOUR_MILI = 60 * 60 * ONE_SECOND_MILI;

    @Value("${link.auth.expired}")
    private long linkAuthExpiredMillis;
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

    /**
     * Payment plan gate: every generated environment consumes one slot of the
     * seller's ACTIVE plan assignment.
     */
    @Autowired
    private com.qrpublic.apartment.domain.paymentplan.PaymentPlanService paymentPlanService;

    @Autowired
    private com.qrpublic.apartment.core.service.CoreTimezoneService coreTimezoneService;

    @Override
    public SaleEnvironment generateSaleEnvironment(Request request) {
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
    public SaleEnvDTO generateSaleEnvironment(CreateEnvironmentRequest request) {
        log.info("{} Creating sale environment for request ID: {}", CommonConstant.START, request.getRequestUuid());
        RequestDTO requestDTO = convertToRequestDTO(request);

        CompletableFuture<SaleEnvironment> environmentFuture = CompletableFuture
                .supplyAsync(() -> createSaleEnvironment(request));
        CompletableFuture<SellerModel> sellerFuture = CompletableFuture
                .supplyAsync(() -> createOrGetSeller(requestDTO));

        SaleEnvironment environment = environmentFuture.join();
        SellerModel sellerModel = sellerFuture.join();

        final String authenToken = sellerModel.getSellerLinkModel() != null
                ? sellerModel.getSellerLinkModel().getToken()
                : CommonConstant.EMPTY;
        sellerModel.getSellerLinkModel().setContextString(
                LinkBuilder.buildAuthenticationLink(requestDTO.getReqUUID(), authenToken));

        log.info("{} Creating sale environment for request ID: {}", CommonConstant.END, request.getRequestUuid());
        return convertToSaleEnvDto(environment, sellerModel.getSellerLinkModel());
    }

    @Transactional
    public SaleEnvironment createSaleEnvironment(CreateEnvironmentRequest requestObject) {
        Request requestEntity = coreRequestService.getRequestByUuid(requestObject.getRequestUuid()).orElseThrow(
                () -> new ResourceNotFoundException("Request not found with UUID: " + requestObject.getRequestUuid()));

        // Read-only gate first: it reports the plan window and its deadline
        // without spending a link slot, so a refused window below cannot leave
        // the seller with one environment less and nothing to show for it.
        final String sellerUsername = requestObject.getSellerRequest().getUsername();
        final com.qrpublic.apartment.core.model.PlanGateModel planPreview = paymentPlanService.evaluate(sellerUsername);
        if (!planPreview.isAllowed()) {
            log.warn("Refused environment creation for [{}]: {}", sellerUsername, planPreview.getReason());
            throw new com.qrpublic.apartment.exception.ApplicationException(planPreview.getMessage(), 403);
        }

        // Resolve the chosen sale window against the plan deadline. Pure
        // validation: nothing has been written yet at this point.
        final SaleWindow window = resolveWindow(requestObject, planPreview.getDeadline());

        // Quota is only consumed once the window is known to be acceptable.
        paymentPlanService.consumeQuotaOrThrow(sellerUsername);

        if (requestObject.getProductRequest() != null) {
            log.info("Step 2: Validating business rules");
            validateProductCreationRequest(requestObject.getProductRequest());
            ProductModel productModel = convertToProductModel(requestObject.getProductRequest());
            coreProductService.createProductProdImage(requestEntity.getReqId(), productModel);
        }
        // Create SaleEnvironment
        SaleEnvironment env = new SaleEnvironment();
        env.setRequest(requestEntity);
        final LinkModel linkModel = linkService.generateSecureUrlUntil(
                requestObject.getRequestUuid(),
                Map.of(LinkConstant.PARAM_USERNAME, sellerUsername),
                window.end());
        log.debug("Generated public link for sale window [{} .. {}]", window.start(), window.end());
        env.setPublicLink(linkModel.getToken());
        env.setWillStartedAt(new java.sql.Timestamp(window.start().getTime()));
        env.setWillEndedAt(new java.sql.Timestamp(window.end().getTime()));
        env.setState(true);
        return repo.save(env);
    }

    private ProductModel convertToProductModel(CreateProductRequest productRequest) {
        ProductModel productModel = new ProductModel();
        productModel.setProductName(productRequest.getName());
        productModel.setQuantity(productRequest.getQuantity());
        productModel.setUnit(Currency.getInstance("VND").getCurrencyCode());
        productModel.setPrice(productRequest.getPrice());
        productModel.setProductType(productRequest.getProductType());
        // productModel.setTotalQuantity(productRequest.getQuantity() *
        // productRequest.getPrice());
        productModel.setPictureModels(
                productRequest.getImageDataList() != null && !productRequest.getImageDataList().isEmpty()
                        ? productRequest.getImageDataList().stream().map(imageData -> {
                            PictureModel pictureModel = new PictureModel();
                            pictureModel.setData(imageData);
                            return pictureModel;
                        }).collect(Collectors.toList())
                        : List.of());
        return productModel;
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
                        // expiry < 5 min => generate new token, then update
                        String sellerName = sellerDTO != null ? sellerDTO.getName() : null;
                        return generateToken(username, sellerName, linkAuthExpiredMillis)
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
                    return generateToken(username, name, linkAuthExpiredMillis)
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

    /**
     * The resolved sale window: start is never in the past, end never past the
     * plan.
     */
    private record SaleWindow(java.util.Date start, java.util.Date end) {
    }

    /**
     * Resolve the sale window the caller asked for. Both dates come from the
     * client and are therefore treated as wishes only:
     * <ul>
     * <li>start - defaults to now, and a start in the past is pulled up to now;
     * a future start schedules the sale (orders are refused until then).</li>
     * <li>end - defaults to now + price.durationHours, clamped to the paid plan
     * deadline exactly as before. When the caller picks an explicit end, it is
     * <b>rejected</b> rather than silently shortened if it lands after the plan
     * end, so the seller sees why the choice was refused.</li>
     * </ul>
     * The public link token is then minted to expire at that end instant, and
     * the window itself is enforced again at order time.
     */
    private SaleWindow resolveWindow(CreateEnvironmentRequest requestObj, java.util.Date planDeadline) {
        final String sellerUsername = requestObj.getSellerRequest().getUsername();
        final java.util.Date now = coreTimezoneService.getNow();

        java.util.Date start = requestObj.getStartAt() != null ? new java.util.Date(requestObj.getStartAt()) : now;
        if (start.before(now)) {
            log.info("Sale window start for [{}] is in the past, starting now instead", sellerUsername);
            start = now;
        }

        java.util.Date end;
        if (requestObj.getEndAt() != null) {
            end = new java.util.Date(requestObj.getEndAt());
            if (planDeadline != null && end.after(planDeadline)) {
                throw new com.qrpublic.apartment.exception.ApplicationException(
                        "The end time cannot be after the plan end ("
                                + DateUtils.dateToLocalTimeString(planDeadline) + ").",
                        400);
            }
        } else {
            if (requestObj.getSellerRequest().getPrice() == null
                    || requestObj.getSellerRequest().getPrice().getDurationHours() <= 0) {
                throw new com.qrpublic.apartment.exception.ApplicationException(
                        "Either endAt or price.durationHours is required.", 400);
            }
            final long requestedMillis = requestObj.getSellerRequest().getPrice().getDurationHours() * ONE_HOUR_MILI;
            end = new java.util.Date(now.getTime() + requestedMillis);
            if (planDeadline != null && end.after(planDeadline)) {
                log.info("Clamping public link expiry for [{}] from {} to plan deadline {}",
                        sellerUsername, end, planDeadline);
                end = planDeadline;
            }
        }

        if (!end.after(start)) {
            throw new com.qrpublic.apartment.exception.ApplicationException(
                    "The end time must be after the start time.", 400);
        }
        if (!end.after(now)) {
            throw new com.qrpublic.apartment.exception.ApplicationException(
                    "Your payment plan has ended. You cannot create more product links. "
                            + "Please contact admin via chat or buy a new plan.",
                    403);
        }
        if (planDeadline != null && start.after(planDeadline)) {
            throw new com.qrpublic.apartment.exception.ApplicationException(
                    "The start time cannot be after the plan end ("
                            + DateUtils.dateToLocalTimeString(planDeadline) + ").",
                    400);
        }
        return new SaleWindow(start, end);
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
        return repo.findFirstByRequestOrderByCreatedAtDesc(request).get().getPublicLink();
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

        // Fetch products with pictures
        if (env.getRequest() != null && env.getRequest().getReqId() != null) {
            List<com.qrpublic.apartment.entity.Product> products = coreProductService
                    .getProductsWithPicturesByRequestId(env.getRequest().getReqId());
            List<PictureDTO> pictures = products.stream()
                    .filter(p -> p.getListPicProMap() != null)
                    .flatMap(p -> p.getListPicProMap().stream())
                    .filter(ppm -> ppm.getPicture() != null)
                    .map(ppm -> new PictureDTO(
                            ppm.getPicture().getLink(),
                            ppm.getPicture().getTitle(),
                            ppm.getPicture().getData()))
                    .toList();
            environmentDetailResponse.setProductPictures(pictures);
        }

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

    /**
     * Validate the product creation request data
     */
    private void validateProductCreationRequest(CreateProductRequest productRequest) {
        if (productRequest == null) {
            throw new IllegalArgumentException("Product request cannot be null");
        }
        if (productRequest.getName() == null || productRequest.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Product name is required");
        }
        if (productRequest.getPrice() < 0) {
            throw new IllegalArgumentException("Product price cannot be negative");
        }
        if (productRequest.getQuantity() < 0) {
            throw new IllegalArgumentException("Product quantity cannot be negative");
        }
    }

    @Override
    public Mono<SaleEnvDTO> extendAuthLink(String requestUuid) {
        SaleEnvironment env = repo.findByRequestReqUuid(requestUuid)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Sale environment not found for request UUID: " + requestUuid));

        String sellerUsername = env.getRequest() != null ? env.getRequest().getSellerName() : null;
        if (StringUtils.isBlank(sellerUsername)) {
            return Mono.error(new RuntimeException("No seller associated with request UUID: " + requestUuid));
        }

        return operatedSellerClient.findUserByUsername(new FindUserAuthenRequest(sellerUsername))
                .switchIfEmpty(Mono.error(new RuntimeException("No auth data found for seller: " + sellerUsername)))
                .flatMap(authData -> {
                    String existingToken = authData.getAuthenticationToken();
                    if (StringUtils.isBlank(existingToken)) {
                        return Mono.error(new RuntimeException("No existing auth token for seller: " + sellerUsername));
                    }

                    ExtendAuthenTokenRequest extendReq = new ExtendAuthenTokenRequest();
                    extendReq.setExistingToken(existingToken);
                    extendReq.setExtensionMillis(15 * 60 * 1000); // 15 minutes

                    return securityCheckClient.extendAuthenToken(extendReq)
                            .flatMap(newTokenResp -> {
                                UserUserAuthRequest updateReq = new UserUserAuthRequest();
                                updateReq.setUserName(sellerUsername);
                                updateReq.setAuthToken(newTokenResp.getAuthenticationToken());
                                updateReq.setExpire(newTokenResp.getExpire());

                                return operatedSellerClient.updateUserAuth(updateReq)
                                        .flatMap(result -> {
                                            if (!result.isSuccess()) {
                                                return Mono.error(new RuntimeException(
                                                        "Failed to update user auth: " + result.getErrorMessage()));
                                            }
                                            String newAuthLink = LinkBuilder.buildAuthenticationLink(
                                                    requestUuid, newTokenResp.getAuthenticationToken());
                                            SaleEnvDTO dto = new SaleEnvDTO();
                                            dto.setSellerAuthLink(newAuthLink);
                                            dto.setSellerAuthLinkExpire(newTokenResp.getExpire());
                                            dto.setRequestUUID(requestUuid);
                                            return Mono.just(dto);
                                        });
                            });
                });
    }

}
