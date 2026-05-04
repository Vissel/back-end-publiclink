package com.qrpublic.apartment.core.service;

import com.qrpublic.apartment.core.model.AuthenticationEnum;
import com.qrpublic.apartment.core.model.RequestModel;
import com.qrpublic.apartment.core.model.UserModel;
import com.qrpublic.apartment.entity.Request;
import com.qrpublic.apartment.entity.User;
import com.qrpublic.apartment.model.SellerDTO;
import com.qrpublic.apartment.repository.RequestRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

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
    public RequestModel generateRequestForSeller(SellerDTO sellerDTO) {
        User seller = coreUserService.findSeller(sellerDTO);
        String sellerName = null;

        if (seller != null) {
            sellerName = seller.getUserName();
        }
        Request req = new Request();
        req.setReqUUID(UUID.randomUUID().toString());
        req.setSellerName(sellerName); // can be null if not found
        req.setAuthenticated(false);

        // Save to database
        return convertToModel(requestRepository.save(req));
    }

    private RequestModel convertToModel(Request request) {
        RequestModel model = new RequestModel();
        model.setRequestUuid(request.getReqUUID());
        UserModel sellerModel = UserModel.builder().build();
        if (request.getSellerName() != null) {
            sellerModel.setUsername(request.getSellerName());
        }
        model.setSeller(sellerModel);
        model.setCreatedAt(request.getCreatedAt().toString());
        model.setAuthentication(request.isAuthenticated() ? AuthenticationEnum.BASIC : AuthenticationEnum.UNAUTHENTICATED);
        return model;
    }
}
