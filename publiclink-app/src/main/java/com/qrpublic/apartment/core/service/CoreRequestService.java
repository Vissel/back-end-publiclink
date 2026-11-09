package com.qrpublic.apartment.core.service;

import com.qrpublic.apartment.core.model.AuthenticationEnum;
import com.qrpublic.apartment.core.model.RequestModel;
import com.qrpublic.apartment.core.model.UserModel;
import com.qrpublic.apartment.entity.Pricing;
import com.qrpublic.apartment.entity.Request;
import com.qrpublic.apartment.entity.User;
import com.qrpublic.apartment.model.SellerDTO;
import com.qrpublic.apartment.repository.RequestRepository;
import com.qrpublic.apartment.requestmodel.PriceRequest;
import com.qrpublic.apartment.util.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class CoreRequestService {
    @Autowired
    RequestRepository requestRepository;

    @Autowired
    CoreUserService coreUserService;

    @Transactional
    public Optional<Request> getRequestById(String requestId) {
        try {
            return requestRepository.findById(Long.parseLong(requestId));
        } catch (NumberFormatException e) {
            log.warn("Invalid request ID format: {}", requestId);
            return Optional.empty();
        }
    }

    @Transactional
    public Optional<Request> getRequestByUuid(String requestUuid) {
        return requestRepository.findByReqUUID(requestUuid);
    }

    @Transactional
    public void updateSellerNameIfMissing(String requestUuid, String sellerName) {
        if (sellerName == null || sellerName.isBlank()) {
            return;
        }
        requestRepository.findByReqUUID(requestUuid).ifPresent(request -> {
            if (request.getSellerName() == null || request.getSellerName().isBlank()) {
                request.setSellerName(sellerName);
                requestRepository.save(request);
                log.info("Updated sellerName '{}' for request uuid: {}", sellerName, requestUuid);
            }
        });
    }

    @Transactional
    public RequestModel generateRequestForSeller(SellerDTO sellerDTO) {
        User seller = coreUserService.findSeller(sellerDTO);
        String sellerName = null;

        if (seller != null) {
            sellerName = seller.getUserName();
        }
        Request requestEntity = new Request();
        requestEntity.setReqUUID(UUID.randomUUID().toString());
        requestEntity.setSellerName(sellerName); // can be null if not found
        requestEntity.setAuthenticated(false);

        // Persist pricing if provided
        if (sellerDTO.getPrice() != null) {
            PriceRequest priceReq = sellerDTO.getPrice();
            Pricing pricingEntity = new Pricing();
            pricingEntity.setDurationHours(priceReq.getDurationHours());
            pricingEntity.setAmount(priceReq.getPriceAmount());
            pricingEntity.setCurrency(priceReq.getCurrency() != null ? priceReq.getCurrency() : "VND");
            pricingEntity.setRequest(requestEntity);
            List<Pricing> pricings = new ArrayList<>();
            pricings.add(pricingEntity);
            requestEntity.setPricings(pricings);
        }

        // Save to database
        return convertToModel(requestRepository.save(requestEntity));
    }

    private RequestModel convertToModel(Request savedRequestEntity) {
        RequestModel model = new RequestModel();
        model.setRequestUuid(savedRequestEntity.getReqUUID());
        UserModel sellerModel = UserModel.builder().build();
        if (savedRequestEntity.getSellerName() != null) {
            sellerModel.setUsername(savedRequestEntity.getSellerName());
        }
        model.setSeller(sellerModel);
        model.setCreatedAt(DateUtils.dateToString(savedRequestEntity.getCreatedAt()));
        model.setAuthentication(
                savedRequestEntity.isAuthenticated() ? AuthenticationEnum.BASIC : AuthenticationEnum.UNAUTHENTICATED);
        return model;
    }
}
