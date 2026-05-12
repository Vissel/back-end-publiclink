package com.qrpublic.apartment.user.service;

import com.qrpublic.apartment.user.entity.UserAuthEntity;
import com.qrpublic.apartment.user.repository.UserAuthEntityRepository;
import com.qrpublic.apartment.user.service.request.FoundUserAuthenRequest;
import com.qrpublic.apartment.user.service.response.FoundUserAuthenResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;

@Service
public class UserAuthenServiceImpl implements UserAuthenService {
    @Autowired
    UserAuthEntityRepository userAuthEntityRepository;

    @Override
    public Mono<FoundUserAuthenResponse> findUserAuthByUsername(FoundUserAuthenRequest foundUserAuthenRequest) {
        return Mono.fromCallable(() -> {
            List<UserAuthEntity> list = userAuthEntityRepository.findByUsername(foundUserAuthenRequest.getUserName());

            UserAuthEntity entity = list.stream()
                    .filter(e -> Boolean.TRUE.equals(e.getIsActive()))
                    .max(Comparator.comparing(UserAuthEntity::getCreatedAt))
                    .orElseThrow(() -> new EmptyResultDataAccessException(
                            "No active auth token found for user: " + foundUserAuthenRequest.getUserName(), 1));

            FoundUserAuthenResponse response = new FoundUserAuthenResponse();
            response.setUserName(entity.getUserEntity().getUsername());
            response.setAuthenticationToken(entity.getAuthToken());
            response.setCreatedAt(entity.getCreatedAt());
            response.setExpiredAt(entity.getExpireAt());
            response.setIsActive(entity.getIsActive());
            response.setExtendedNum(entity.getExtendedNum());
            return response;
        });
    }
}
