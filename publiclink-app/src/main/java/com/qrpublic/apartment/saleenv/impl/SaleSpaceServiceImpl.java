package com.qrpublic.apartment.saleenv.impl;

import com.qrpublic.apartment.constant.CommonConstant;
import com.qrpublic.apartment.constant.LinkConstant;
import com.qrpublic.apartment.core.service.CoreRequestService;
import com.qrpublic.apartment.core.service.CoreUserService;
import com.qrpublic.apartment.entity.Order;
import com.qrpublic.apartment.entity.Product;
import com.qrpublic.apartment.entity.Request;
import com.qrpublic.apartment.entity.SaleEnvironment;
import com.qrpublic.apartment.exception.EnvironmentCreationException;
import com.qrpublic.apartment.integration.SecurityCheckClient;
import com.qrpublic.apartment.model.SellerDTO;
import com.qrpublic.apartment.repository.SaleEnvironmentRepository;
import com.qrpublic.apartment.requestmodel.OrderDTO;
import com.qrpublic.apartment.requestmodel.PictureDTO;
import com.qrpublic.apartment.requestmodel.ProductDTO;
import com.qrpublic.apartment.saleenv.SaleSpaceService;
import com.qrpublic.apartment.saleenv.request.GetSaleSpaceRequest;
import com.qrpublic.apartment.saleenv.response.GetSaleSpaceResponse;
import com.qrpublic.apartment.service.LinkService;
import com.qrpublic.apartment.template.model.Result;
import com.qrpublic.apartment.template.service.ProcessCallback;
import com.qrpublic.apartment.template.service.PublicLinkServiceTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Stream;

@Service
public class SaleSpaceServiceImpl implements SaleSpaceService {

    @Autowired
    PublicLinkServiceTemplate publicLinkServiceTemplate;

    @Autowired
    LinkService linkService;

    @Autowired
    CoreRequestService coreRequestService;

    @Autowired
    CoreUserService coreUserService;

    @Autowired
    SaleEnvironmentRepository saleEnvironmentRepository;

    @Autowired
    SecurityCheckClient securityCheckClient;

    @Override
    public Result<GetSaleSpaceResponse> getSaleSpace(GetSaleSpaceRequest getSaleSpaceRequest) {
        return publicLinkServiceTemplate.execute(new ProcessCallback<GetSaleSpaceRequest, GetSaleSpaceResponse>() {

            @Override
            public GetSaleSpaceRequest getRequest() {
                return getSaleSpaceRequest;
            }

            @Override
            public void preProcess(GetSaleSpaceRequest request) {
                if (!linkService.validateLink(request.getToken())) {
                    throw new IllegalArgumentException("Invalid or expired token.");
                }
            }

            @Override
            public GetSaleSpaceResponse process() {
                String token = getRequest().getToken();

                // 1. Extract subject (requestUUID) from token, get Request
                String requestUUID = (String) linkService.extractClaimByKey(token, LinkConstant.CLAIM_SUBJECT);
                Optional<Request> requestOpt = coreRequestService.getRequestByUuid(requestUUID);

                // 2. Extract username claim, find Seller (empty if not found)
                String username = (String) linkService.extractClaimByKey(token, LinkConstant.PARAM_USERNAME);
                String sellerName =
                        Stream.of(username).filter(Objects::nonNull)
                                .map(u -> {
                                    SellerDTO sellerDTO = new SellerDTO();
                                    sellerDTO.setUsername(u);
                                    return coreUserService.findSeller(sellerDTO);
                                })
                                .map(com.qrpublic.apartment.entity.User::getName)
                                .findFirst()
                                .orElse(null);

                // 3. Call check-token to validate token
                Boolean tokenValid = Boolean.TRUE.equals(securityCheckClient.checkToken(token).block());

                // 4. Get SaleEnvironment from Request, fetch list order and list product
                SaleEnvironment saleEnvironment = requestOpt.flatMap(saleEnvironmentRepository::findByRequest)
                        .orElseThrow(() -> new EnvironmentCreationException("Environment not found:" + requestUUID));
                // TODO - remain the payment method,... waiting for FE integration
                return buildGetSaleSpaceResponse(requestOpt, saleEnvironment, sellerName, requestUUID, tokenValid);
            }
        });
    }

    private GetSaleSpaceResponse buildGetSaleSpaceResponse(Optional<Request> requestOpt,
                                                           SaleEnvironment saleEnvironment,
                                                           String sellerName,
                                                           String requestUUID,
                                                           Boolean tokenValid) {
        GetSaleSpaceResponse response = new GetSaleSpaceResponse();
        response.setReqUuid(requestUUID);
        response.setSellerName(sellerName);
        response.setIsSellerView(tokenValid);

        response.setPublicLink(saleEnvironment.getPublicLink());
        response.setEnvState(String.valueOf(saleEnvironment.isState()));
        if (saleEnvironment.getCreatedAt() != null) {
            response.setCreatedAt(saleEnvironment.getCreatedAt().toString());
        }
        if (saleEnvironment.getEndedAt() != null) {
            response.setEndedAt(saleEnvironment.getEndedAt().toString());
        }
        if (tokenValid) {
            response.setListOrder(toOrderDTOs(saleEnvironment.getListOrder(), saleEnvironment.getPublicLink()));
        }

        requestOpt.ifPresent(req ->
                response.setListProduct(toProductDTOs(req.getProducts())));

        return response;
    }

    private List<OrderDTO> toOrderDTOs(List<Order> orders, String publicLink) {
        if (orders == null || orders.isEmpty()) {
            return Collections.emptyList();
        }
        SimpleDateFormat formatter = new SimpleDateFormat(CommonConstant.DATETIME_PATTERN);
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        List<OrderDTO> result = new ArrayList<>();
        for (Order o : orders) {
            String orderedTime = o.getOrderedAt() != null
                    ? formatter.format(new Date(o.getOrderedAt().getTime()))
                    : null;
            result.add(new OrderDTO(o.getOrderId(), orderedTime, o.getBuyerName(), publicLink,
                    o.isDelivered(), o.isGetMoney(), o.getSellerNote(), o.getAmount(), o.getUnit(), o.getNote()));
        }
        return result;
    }

    private List<ProductDTO> toProductDTOs(List<Product> products) {
        if (products == null || products.isEmpty()) {
            return Collections.emptyList();
        }
        List<ProductDTO> result = new ArrayList<>();
        for (Product p : products) {
            List<PictureDTO> pictures = Collections.emptyList();
            if (p.getListPicProMap() != null) {
                pictures = p.getListPicProMap().stream()
                        .filter(map -> map.getPicture() != null)
                        .map(map -> new PictureDTO(map.getPicture().getLink(), map.getPicture().getTitle()))
                        .toList();
            }
            result.add(new ProductDTO(p.getProductName(), p.getAmount(), p.getUnit(),
                    p.getPrice(), p.getTotal_amount(), pictures));
        }
        return result;
    }
}
