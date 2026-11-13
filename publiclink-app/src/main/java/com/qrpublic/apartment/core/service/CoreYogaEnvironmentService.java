package com.qrpublic.apartment.core.service;

import com.qrpublic.apartment.entity.Product;
import com.qrpublic.apartment.entity.Request;
import com.qrpublic.apartment.entity.SaleEnvironment;
import com.qrpublic.apartment.exception.ResourceNotFoundException;
import com.qrpublic.apartment.repository.ProductRepository;
import com.qrpublic.apartment.repository.RequestRepository;
import com.qrpublic.apartment.repository.SaleEnvironmentRepository;
import com.qrpublic.apartment.core.model.YogaEnvModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Core-layer resolution of sale environments for the yoga feature. The only
 * place that turns a request UUID / env id into the YogaEnvModel DTO; the
 * domain layer never sees SaleEnvironment or Product entities.
 */
@Slf4j
@Service
public class CoreYogaEnvironmentService {

    public static final String TYPE_YOGA = "YOGA_BOOKING";
    public static final String TYPE_STANDARD = "STANDARD";

    @Autowired
    private RequestRepository requestRepository;

    @Autowired
    private SaleEnvironmentRepository saleEnvironmentRepository;

    @Autowired
    private ProductRepository productRepository;

    /**
     * Resolve from the request UUID carried by the public link token subject.
     */
    @Transactional(readOnly = true)
    public YogaEnvModel resolveByRequestUuid(String requestUuid) {
        Request request = requestRepository.findByReqUUID(requestUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found: " + requestUuid));
        SaleEnvironment env = saleEnvironmentRepository.findFirstByRequestOrderByCreatedAtDesc(request)
                .orElseThrow(() -> new ResourceNotFoundException("Environment not found for request: " + requestUuid));
        return toModel(env);
    }

    @Transactional(readOnly = true)
    public YogaEnvModel resolveByEnvId(String envId) {
        SaleEnvironment env = saleEnvironmentRepository.findById(envId)
                .orElseThrow(() -> new ResourceNotFoundException("Environment not found: " + envId));
        return toModel(env);
    }

    /**
     * Yoga environments owned by a seller, newest first. Used by the seller
     * setup page environment selector.
     */
    @Transactional(readOnly = true)
    public List<YogaEnvModel> findYogaEnvsBySeller(String sellerUsername) {
        return saleEnvironmentRepository.findBySellerUsername(sellerUsername).stream()
                .map(this::toModel)
                .filter(model -> TYPE_YOGA.equals(model.getProductType()))
                .toList();
    }

    /**
     * True when the environment's first product is a yoga booking product.
     */
    @Transactional(readOnly = true)
    public boolean isYogaEnv(String envId) {
        return TYPE_YOGA.equals(resolveByEnvId(envId).getProductType());
    }

    private YogaEnvModel toModel(SaleEnvironment env) {
        Request request = env.getRequest();
        List<Product> products = productRepository.findProductsByRequest(request);
        Product first = products.isEmpty() ? null : products.get(0);

        String productType = first != null && first.getProductType() != null
                ? first.getProductType()
                : TYPE_STANDARD;
        BigDecimal dropInPrice = first != null && first.getPrice() != null
                ? BigDecimal.valueOf(first.getPrice())
                : null;

        return YogaEnvModel.builder()
                .envId(env.getEnvId())
                .requestUUID(request.getReqUUID())
                .publicLink(env.getPublicLink())
                .sellerUsername(request.getSellerName())
                .active(env.isState())
                .willEndedAtMillis(env.getWillEndedAt() != null ? env.getWillEndedAt().getTime() : null)
                .productType(productType)
                .productName(first != null ? first.getProductName() : null)
                .dropInPrice(dropInPrice)
                .build();
    }
}
