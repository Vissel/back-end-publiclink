package com.qrpublic.apartment.saleenv.impl;

import com.qrpublic.apartment.constant.CommonConstant;
import com.qrpublic.apartment.constant.LinkConstant;
import com.qrpublic.apartment.core.linkBuilder.LinkBuilder;
import com.qrpublic.apartment.core.model.LinkModel;
import com.qrpublic.apartment.core.model.SellerModel;
import com.qrpublic.apartment.core.service.CoreEnvironmentService;
import com.qrpublic.apartment.core.service.CoreRequestService;
import com.qrpublic.apartment.entity.Request;
import com.qrpublic.apartment.entity.SaleEnvironment;
import com.qrpublic.apartment.exception.ResourceNotFoundException;
import com.qrpublic.apartment.repository.SaleEnvironmentRepository;
import com.qrpublic.apartment.requestmodel.Pagination;
import com.qrpublic.apartment.requestmodel.RequestDTO;
import com.qrpublic.apartment.requestmodel.SaleEnvDTO;
import com.qrpublic.apartment.saleenv.SaleEnvironmentService;
import com.qrpublic.apartment.saleenv.convertor.SaleEnvConvertor;
import com.qrpublic.apartment.saleenv.request.CreateEnvironmentRequest;
import com.qrpublic.apartment.saleenv.request.ListEnvironmentRequest;
import com.qrpublic.apartment.service.LinkService;
import com.qrpublic.apartment.template.model.Result;
import com.qrpublic.apartment.template.service.ProcessCallback;
import com.qrpublic.apartment.template.service.PublicLinkServiceTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@PreAuthorize("hasRole('Admin')")
public class SaleEnvironmentServiceImpl implements SaleEnvironmentService {

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

    @Override
    public SaleEnvironment createSaleEnvironment(Request request) {
        log.info("Creating sale environment.");
        SaleEnvironment env = repo.save(new SaleEnvironment(request));
        if (env.getEnvId() != null && !env.getEnvId().isBlank()) {
            final String publicLink = "";
//                    generatePublicLink(env, request);
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
     * @param request the create environment request containing seller details and request ID
     * @return Mono of CreateEnvironmentResponse with error handling
     */
    @Override
    public SaleEnvDTO createSaleEnvironment(CreateEnvironmentRequest request) {
        log.info("{} Creating sale environment for request ID: {}", CommonConstant.START, request.getRequestUuid());
        RequestDTO requestDTO = convertToRequestDTO(request);

        // create sale environment including public link
        SaleEnvironment environment = createSaleEnvironment(requestDTO);
        // create auth link and add to response
        LinkModel authLink = generateReqAuthLink(requestDTO);
        SellerModel sellerModel = createOrGetSeller(requestDTO);

        log.info("{} Creating sale environment for request ID: {}", CommonConstant.END, request.getRequestUuid());
        return convertToSaleEnvDto(environment, authLink);
    }

    private SaleEnvironment createSaleEnvironment(RequestDTO requestDTO) {
        Request requestEntity = coreRequestService.getRequestByUuid(requestDTO.getReqUUID()).orElseThrow(() -> new ResourceNotFoundException("Request not found with UUID: " + requestDTO.getReqUUID()));

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
        // findSeller from
        // updateSellerTokenById


    }

    private RequestDTO convertToRequestDTO(CreateEnvironmentRequest request) {
        RequestDTO requestDTO = new RequestDTO();
        requestDTO.setReqUUID(request.getRequestUuid());
        requestDTO.setUsername(request.getSellerRequest().getUsername());
        return requestDTO;
    }

    private LinkModel generatePublicLink(RequestDTO requestDTO) {
        return linkService.generateSecureUrl(requestDTO.getReqUUID(), Map.of(LinkConstant.PARAM_USERNAME, requestDTO.getUsername()));
    }

    private LinkModel generateReqAuthLink(RequestDTO requestDTO) {
        LinkModel linkModel = linkService.generateAuthLink(Map.of(LinkConstant.PARAM_USERNAME, requestDTO.getUsername()));
        linkModel.setContextString(LinkBuilder.buildAuthenticationLink(requestDTO.getReqUUID(), linkModel.getToken()));
        return linkModel;
    }

    @Override
    public Result<List<SaleEnvDTO>> getAllEnvironment(Pagination<ListEnvironmentRequest> listEnvironmentRequestPagination) {
        return publicLinkServiceTemplate.execute(new ProcessCallback<Pagination<ListEnvironmentRequest>, List<SaleEnvDTO>>() {
            @Override
            public Pagination<ListEnvironmentRequest> getRequest() {
                return listEnvironmentRequestPagination;
            }

            @Override
            public void preProcess(Pagination<ListEnvironmentRequest> request) {

            }

            @Override
            public List<SaleEnvDTO> process() {
                PageRequest pageable = PageRequest.of(getRequest().getPage(), getRequest().getSize(), Sort.by(Sort.Order.desc("createdAt")));
                return coreEnvironmentService.getEnvironments(pageable).stream()
                        .map(SaleEnvConvertor::buildSaleEnvDTOFromModel).toList();
            }
        });
    }

    @Override
    public SaleEnvironment getEnvironmentByPublicLink(String publicLink) {
        return repo.findByPublicLink(publicLink).orElse(null);
    }

    @Override
    public String getPublicLinkBy(Request request) {
        return repo.findByRequest(request).get().getPublicLink();
    }

    private SaleEnvDTO convertToSaleEnvDto(SaleEnvironment env, LinkModel linkModel) {
        return SaleEnvConvertor.buildEnvDTO(env)
                .sellerAuthLink(linkModel.getContextString())
                .sellerAuthLinkExpire(linkModel.getExpire())
                .createdBy(linkModel.getIssueAt().toString())
                .build();
    }
}
