package com.qrpublic.apartment.saleenv.impl;

import com.qrpublic.apartment.constant.CommonConstant;
import com.qrpublic.apartment.constant.LinkConstant;
import com.qrpublic.apartment.core.model.LinkModel;
import com.qrpublic.apartment.core.service.CoreRequestService;
import com.qrpublic.apartment.entity.Request;
import com.qrpublic.apartment.entity.SaleEnvironment;
import com.qrpublic.apartment.exception.ResourceNotFoundException;
import com.qrpublic.apartment.model.convertor.OrderConvertor;
import com.qrpublic.apartment.repository.SaleEnvironmentRepository;
import com.qrpublic.apartment.requestmodel.OrderDTO;
import com.qrpublic.apartment.requestmodel.RequestDTO;
import com.qrpublic.apartment.requestmodel.SaleEnvDTO;
import com.qrpublic.apartment.saleenv.SaleEnvironmentService;
import com.qrpublic.apartment.saleenv.request.CreateEnvironmentRequest;
import com.qrpublic.apartment.service.LinkService;
import com.qrpublic.apartment.util.Utils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
    LinkService linkService;

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

        log.info("{} Creating sale environment for request ID: {}", CommonConstant.END, request.getRequestUuid());
        return convertToSaleEnvDto(environment, authLink);
    }

    private SaleEnvironment createSaleEnvironment(RequestDTO requestDTO) {
        Request requestEntity = coreRequestService.getRequestByUuid(requestDTO.getReqUUID())
                .orElseThrow(() -> new ResourceNotFoundException("Request not found with UUID: " + requestDTO.getReqUUID()));

        // Create SaleEnvironment
        SaleEnvironment env = new SaleEnvironment();
        env.setRequest(requestEntity);
        // Generate public link by username + requestID
        final LinkModel linkModel = generatePublicLink(requestDTO);
        final String publicLink = linkModel.getLink();
        log.debug("Generated public link: {}", publicLink);
        env.setPublicLink(publicLink);
        return repo.save(env);
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
        return linkService.generateAuthLink(Map.of(LinkConstant.PARAM_USERNAME, requestDTO.getUsername()));
    }

    @Override
    public List<SaleEnvDTO> getAllEnvironment() {
        List<SaleEnvironment> entities = repo.findAll(Sort.by(Order.desc("createdAt")));
        return entities.stream().map(entity -> buildEnvDTO(entity)).toList();
    }

    @Override
    public SaleEnvironment getEnvironmentByPublicLink(String publicLink) {
        return repo.findByPublicLink(publicLink).orElse(null);
    }

    @Override
    public String getPublicLinkBy(Request request) {
        return repo.findByRequest(request).get().getPublicLink();
    }

    private SaleEnvDTO buildEnvDTO(SaleEnvironment env) {
        String productName = CommonConstant.EMPTY;
        if (!env.getRequest().getProducts().isEmpty()) {
            productName = env.getRequest().getProducts().getFirst().getProductName();
        }
        List<OrderDTO> orders = createListOrderDTO(env.getListOrder());

        return SaleEnvDTO.builder()
                .createdAt(Utils.formatTimeStamp(env.getCreatedAt()))
                .sellerName(env.getRequest().getSellerName())
                .productName(productName)
                .publicLink(env.getPublicLink())
                .createdBy(env.getRequest().getCreatedBy().getName())
                .envStatus(env.isState())
                .orders(orders)
                .requestUUID(env.getRequest().getReqUUID())
                .build();
    }

    private SaleEnvDTO convertToSaleEnvDto(SaleEnvironment env, LinkModel linkModel) {
        return buildEnvDTO(env)
                .builder()
                .sellerAuthLink(linkModel.getLink())
                .sellerAuthLinkExpire(linkModel.getExpire())
                .createdBy(linkModel.getIssueAt().toString())
                .build();
    }

    private static List<OrderDTO> createListOrderDTO(List<com.qrpublic.apartment.entity.Order> listOrder) {
        List<OrderDTO> orderDTOs = new ArrayList<>();
        if (listOrder != null && !listOrder.isEmpty()) {
            String publicLink = listOrder.get(0).getSaleEnvironment().getPublicLink();
            listOrder.stream().forEach(o -> orderDTOs.add(OrderConvertor.createOrderDTO(o, o.getOrderedAt().getTime(), publicLink)));
        }
        return orderDTOs;
    }


}
