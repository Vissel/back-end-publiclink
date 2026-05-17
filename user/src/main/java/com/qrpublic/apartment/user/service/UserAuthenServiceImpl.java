package com.qrpublic.apartment.user.service;

import com.qrpublic.apartment.user.entity.UserAuthEntity;
import com.qrpublic.apartment.user.exception.UserErrorEnum;
import com.qrpublic.apartment.user.repository.UserAuthEntityRepository;
import com.qrpublic.apartment.user.service.request.FoundUserAuthenRequest;
import com.qrpublic.apartment.user.service.request.UpdateUserAuthRequest;
import com.qrpublic.apartment.user.service.response.FoundUserAuthenResponse;
import com.qrpublic.apartment.user.service.response.UpdateUserAuthResponse;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
public class UserAuthenServiceImpl implements UserAuthenService {
    @Autowired
    UserAuthEntityRepository userAuthEntityRepository;

    @Override
    public Mono<FoundUserAuthenResponse> findUserAuthByUsername(FoundUserAuthenRequest foundUserAuthenRequest) {
        return Mono.fromCallable(() -> {
            List<UserAuthEntity> list = userAuthEntityRepository.findByUsername(foundUserAuthenRequest.getUserName());

            return list.stream()
                    .filter(e -> Boolean.TRUE.equals(e.getIsActive()))
                    .max(Comparator.comparing(UserAuthEntity::getCreatedAt))
                    .map(entity -> {
                        FoundUserAuthenResponse response = new FoundUserAuthenResponse();
                        response.setUserName(entity.getUser().getUsername());
                        response.setAuthenticationToken(entity.getAuthToken());
                        response.setCreatedAt(entity.getCreatedAt());
                        response.setExpiredAt(entity.getExpireAt());
                        response.setIsActive(entity.getIsActive());
                        response.setExtendedNum(entity.getExtendedNum());
                        return response;
                    })
                    .orElse(new FoundUserAuthenResponse());
        });
    }

    @Override
    public Mono<UpdateUserAuthResponse> updateUserAuth(UpdateUserAuthRequest request) {
        return Mono.fromCallable(() -> doUpdateUserAuth(request))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(ex -> log.error("Failed to update user auth for {}", request.getUserName(), ex))
                .onErrorResume(ex -> {
                    UpdateUserAuthResponse error = new UpdateUserAuthResponse();
                    error.setSuccess(false);
                    error.setErrorCode(UserErrorEnum.USER_CREATE_AUTH_ERROR.getCode());
                    error.setErrorMessage(ex.getMessage());
                    return Mono.just(error);
                });
    }

    @Override
    public Mono<UpdateUserAuthResponse> invalidateUserAuthByUsername(String username) {
        return Mono.fromCallable(() -> doInvalidateUserAuth(username))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(ex -> log.error("Failed to invalidate user auth for {}", username, ex))
                .onErrorResume(ex -> {
                    UpdateUserAuthResponse error = new UpdateUserAuthResponse();
                    error.setSuccess(false);
                    error.setErrorCode(UserErrorEnum.USER_CREATE_AUTH_ERROR.getCode());
                    error.setErrorMessage(ex.getMessage());
                    return Mono.just(error);
                });
    }

    @Transactional(rollbackFor = Exception.class)
    protected UpdateUserAuthResponse doUpdateUserAuth(UpdateUserAuthRequest request) {
        List<UserAuthEntity> list = userAuthEntityRepository.findByUsername(request.getUserName());
        UserAuthEntity auth = list.stream()
                .filter(e -> Boolean.TRUE.equals(e.getIsActive()))
                .max(Comparator.comparing(UserAuthEntity::getCreatedAt))
                .orElse(new UserAuthEntity());

        auth.setAuthToken(request.getAuthToken());
        auth.setExpireAt(new java.sql.Timestamp(request.getExpire().getTime()));
        userAuthEntityRepository.save(auth);

        UpdateUserAuthResponse response = new UpdateUserAuthResponse();
        response.setSuccess(true);
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    protected UpdateUserAuthResponse doInvalidateUserAuth(String username) {
        Assert.isTrue(StringUtils.isNotBlank(username), "username must not be null or empty.");
        List<UserAuthEntity> list = userAuthEntityRepository.findByUsername(username);
        if (list.isEmpty()) {
            UpdateUserAuthResponse response = new UpdateUserAuthResponse();
            response.setSuccess(false);
            response.setErrorCode(UserErrorEnum.USER_CREATE_AUTH_ERROR.getCode());
            response.setErrorMessage("User auth not found for username: " + username);
            return response;
        }

        UserAuthEntity auth = list.stream()
                .filter(e -> Boolean.TRUE.equals(e.getIsActive()))
                .max(Comparator.comparing(UserAuthEntity::getCreatedAt))
                .orElse(null);

        if (auth == null) {
            UpdateUserAuthResponse response = new UpdateUserAuthResponse();
            response.setSuccess(false);
            response.setErrorCode(UserErrorEnum.USER_CREATE_AUTH_ERROR.getCode());
            response.setErrorMessage("No active user auth found for username: " + username);
            return response;
        }

        // Invalidate by setting expire time to now and deactivating
        auth.setIsActive(false);
        auth.setExpireAt(new java.sql.Timestamp(System.currentTimeMillis()));
        userAuthEntityRepository.save(auth);

        log.info("Invalidated user auth for username: {}", username);
        UpdateUserAuthResponse response = new UpdateUserAuthResponse();
        response.setSuccess(true);
        return response;
    }
}
