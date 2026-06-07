package com.qrpublic.apartment.saleenv.impl;

import com.qrpublic.apartment.constant.CommonConstant;
import com.qrpublic.apartment.constant.HeaderConstant;
import com.qrpublic.apartment.constant.LinkConstant;
import com.qrpublic.apartment.core.model.EnvStateEnum;
import com.qrpublic.apartment.core.model.UserModel;
import com.qrpublic.apartment.core.service.CoreRequestService;
import com.qrpublic.apartment.core.service.CoreTimezoneService;
import com.qrpublic.apartment.core.service.CoreUserService;
import com.qrpublic.apartment.entity.*;
import com.qrpublic.apartment.exception.EnvironmentCreationException;
import com.qrpublic.apartment.integration.SecurityCheckClient;
import com.qrpublic.apartment.repository.ProductRepository;
import com.qrpublic.apartment.repository.SaleEnvironmentRepository;
import com.qrpublic.apartment.requestmodel.OrderDTO;
import com.qrpublic.apartment.requestmodel.PictureDTO;
import com.qrpublic.apartment.requestmodel.ProductDTO;
import com.qrpublic.apartment.requestmodel.UpdateProductRequest;
import com.qrpublic.apartment.saleenv.SaleSpaceService;
import com.qrpublic.apartment.saleenv.response.GetSaleSpaceResponse;
import com.qrpublic.apartment.service.LinkService;
import com.qrpublic.apartment.template.model.Result;
import com.qrpublic.apartment.template.service.ProcessCallback;
import com.qrpublic.apartment.template.service.PublicLinkServiceTemplate;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Stream;

@Slf4j
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
    ProductRepository productRepository;

    @Autowired
    CoreTimezoneService coreTimezoneService;

    @Autowired
    SecurityCheckClient securityCheckClient;

    @Override
    public Result<GetSaleSpaceResponse> getSaleSpace(String token, Map<String, Object> headers) {
        return publicLinkServiceTemplate.execute(new ProcessCallback<Void, GetSaleSpaceResponse>() {

            @Override
            public Void getRequest() {
                return null;
            }

            @Override
            public void preProcess(Void request) {
                if (!linkService.validateLink(token)) {
                    throw new IllegalArgumentException("Invalid or expired token.");
                }
            }

            @Override
            public GetSaleSpaceResponse process() {
                final String loggedInUsername = getHeader(headers, HeaderConstant.USER_ID_HEADER);
                // 1. Extract subject (requestUUID) from token, get Request
                String requestUUID = (String) linkService.extractClaimByKey(token, LinkConstant.CLAIM_SUBJECT);
                Optional<Request> requestOpt = coreRequestService.getRequestByUuid(requestUUID);

                // 2. Extract usernameOfEnv claim, find Seller (empty if not found)
                String usernameOfEnv = (String) linkService.extractClaimByKey(token, LinkConstant.PARAM_USERNAME);
                UserModel userModel = findUserByUsername(usernameOfEnv);
                boolean isValidSellerView = checkValidSellerView(userModel, loggedInUsername, headers);

                // 4. Get SaleEnvironment from Request, fetch list order and list product
                SaleEnvironment saleEnvironment = requestOpt
                        .flatMap(saleEnvironmentRepository::findFirstByRequestOrderByCreatedAtDesc)
                        .orElseThrow(() -> new EnvironmentCreationException("Environment not found:" + requestUUID));

                String envId = saleEnvironment.getEnvId();
                List<Order> orders = saleEnvironmentRepository.findWithOrdersById(envId)
                        .map(SaleEnvironment::getListOrder)
                        .orElse(Collections.emptyList());

                // Fetch products with pictures using separate query to avoid multi-bag fetch
                // issue
                List<Product> products = saleEnvironmentRepository.findWithRequestById(envId)
                        .map(se -> se.getRequest().getReqId())
                        .map(productRepository::findProductsWithPicturesByRequestId)
                        .orElse(Collections.emptyList());

                return buildGetSaleSpaceResponse(saleEnvironment, orders, products, userModel, isValidSellerView,
                        requestUUID);
            }

            private boolean checkValidSellerView(UserModel userModel, String loggedInUsername,
                                                 Map<String, Object> headers) {
                if (userModel != null) {
                    final String sellerName = userModel.getUsername();
                    if (StringUtils.isNotBlank(sellerName) && sellerName.equals(loggedInUsername)) {
                        String authHeader = getHeader(headers, HeaderConstant.AUTHORIZATION);
                        if (authHeader != null && authHeader.startsWith(HeaderConstant.BEARER_PREFIX)) {
                            String authToken = authHeader.substring(HeaderConstant.BEARER_PREFIX.length());
                            return securityCheckClient.checkToken(authToken).block();
                        }
                    }
                }
                return false;
            }

            /**
             * Case-insensitive header lookup.
             * Spring WebFlux + Netty normalises header names to lowercase,
             * so a plain Map.get("X-User-ID") returns null.
             */
            private String getHeader(Map<String, Object> headers, String name) {
                if (headers == null || name == null) {
                    return null;
                }
                // Try exact match first
                Object val = headers.get(name);
                if (val != null) {
                    return val.toString();
                }
                // Fall back to case-insensitive search
                String lower = name.toLowerCase();
                for (Map.Entry<String, Object> entry : headers.entrySet()) {
                    if (entry.getKey().toLowerCase().equals(lower)) {
                        return entry.getValue() != null ? entry.getValue().toString() : null;
                    }
                }
                return null;
            }

            private UserModel findUserByUsername(String username) {
                return Stream.of(username).filter(Objects::nonNull)
                        .map(u -> {
                            return coreUserService.findByUsername(u);
                        })
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null);
            }
        });
    }

    @Override
    public String getDBTimezone() {
        return coreTimezoneService.getDatabaseTimeZone().getDisplayName();
    }

    private GetSaleSpaceResponse buildGetSaleSpaceResponse(SaleEnvironment saleEnvironment,
                                                           List<Order> orders,
                                                           List<Product> products,
                                                           UserModel userModel,
                                                           boolean isValidSellerView,
                                                           String requestUUID) {
        GetSaleSpaceResponse response = new GetSaleSpaceResponse();
        response.setReqUuid(requestUUID);
        response.setSellerFullName(userModel.getName());
        if (isValidSellerView) {
            response.setIsSellerView(true);
        }

        response.setPublicLink(saleEnvironment.getPublicLink());
        response.setEnvState(saleEnvironment.isState() ? EnvStateEnum.ACTIVE.name() : EnvStateEnum.INACTIVE.name());
        if (saleEnvironment.getCreatedAt() != null) {
            response.setCreatedAt(saleEnvironment.getCreatedAt().toString());
        }
        if (saleEnvironment.getEndedAt() != null) {
            response.setEndedAt(saleEnvironment.getEndedAt().toString());
        }
        response.setPlannedEndedAt(saleEnvironment.getWillEndedAt() != null
                ? saleEnvironment.getWillEndedAt().toString()
                : null);
        response.setListOrder(toOrderDTOs(orders, saleEnvironment.getPublicLink()));
        response.setListProduct(toProductDTOs(products));
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

    @Override
    @Transactional
    public boolean updateProduct(UpdateProductRequest req) {
        if (!linkService.validateLink(req.getToken())) {
            throw new IllegalArgumentException("Invalid or expired token.");
        }
        Optional<Product> opt = productRepository.findByIdWithPicMaps(req.getProductId());
        if (opt.isEmpty()) {
            throw new IllegalArgumentException("Product not found: " + req.getProductId());
        }
        Product product = opt.get();
        product.setProductName(req.getProductName());
        product.setAmount(req.getAmount());
        product.setUnit(req.getUnit());
        product.setPrice(req.getPrice());
//        product.setTotal_amount(req.getTotalAmount());

        // Replace images: manually remove old mappings, then add new ones
        if (req.getListPicProMap() != null) {
            // Remove existing mappings — must break bidirectional link
            // because product_id is non-nullable (optional = false)
            if (product.getListPicProMap() != null) {
                for (ProductPictureMap ppm : new ArrayList<>(product.getListPicProMap())) {
                    ppm.setProduct(null);
                    product.getListPicProMap().remove(ppm);
                }
            }
            // Add new mappings with proper bidirectional links
            List<ProductPictureMap> newPics = new ArrayList<>();
            for (PictureDTO picDTO : req.getListPicProMap()) {
                Picture picture = new Picture();
                picture.setLink(picDTO.getLink());
                picture.setTitle(picDTO.getTitle());
                picture.setData(picDTO.getData());
                ProductPictureMap ppm = new ProductPictureMap(product, picture);
                newPics.add(ppm);
            }
            product.setListPicProMap(newPics);
        }

        productRepository.save(product);
        log.info("Updated product '{}' (id={}) for token={}",
                product.getProductName(), product.getProductId(), req.getToken());
        return true;
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
                        .map(map -> new PictureDTO(map.getPicture().getLink(), map.getPicture().getTitle(),
                                map.getPicture().getData()))
                        .toList();
            }
            ProductDTO dto = new ProductDTO(p.getProductName(), p.getAmount(), p.getUnit(),
                    p.getPrice(), p.getTotal_amount(), pictures);
            dto.setProductId(p.getProductId());
            result.add(dto);
        }
        return result;
    }
}
