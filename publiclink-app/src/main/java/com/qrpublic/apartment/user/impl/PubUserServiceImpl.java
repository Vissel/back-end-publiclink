package com.qrpublic.apartment.user.impl;

import com.qrpublic.apartment.adapter.authentication.request.FindUserRequest;
import com.qrpublic.apartment.adapter.user.request.ProfileLink;
import com.qrpublic.apartment.adapter.user.request.UserRegisterRequest;
import com.qrpublic.apartment.adapter.user.request.UserRemoveRequest;
import com.qrpublic.apartment.adapter.user.response.UserRegisterResponse;
import com.qrpublic.apartment.core.model.UserModel;
import com.qrpublic.apartment.core.service.CoreUserService;
import com.qrpublic.apartment.entity.User;
import com.qrpublic.apartment.exception.ResourceNotFoundException;
import com.qrpublic.apartment.integration.OperatedUserClient;
import com.qrpublic.apartment.model.SellerDTO;
import com.qrpublic.apartment.model.UserType;
import com.qrpublic.apartment.requestmodel.Pagination;
import com.qrpublic.apartment.template.model.Result;
import com.qrpublic.apartment.template.service.ProcessCallback;
import com.qrpublic.apartment.template.service.PublicLinkServiceTemplate;
import com.qrpublic.apartment.user.PubUserService;
import com.qrpublic.apartment.user.request.GetSellerRequest;
import com.qrpublic.apartment.user.request.ListUserRequest;
import com.qrpublic.apartment.user.request.SellerRegisterRequest;
import com.qrpublic.apartment.user.response.GetUserResponse;
import com.qrpublic.apartment.user.response.ListUserResponse;
import com.qrpublic.apartment.user.response.SellerRegisterResponse;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PubUserServiceImpl implements PubUserService {
    @Autowired
    CoreUserService coreUserService;

    @Autowired
    OperatedUserClient operatedUserClient;

    @Autowired
    PublicLinkServiceTemplate publicLinkServiceTemplate;

    @Override
    public Result<ListUserResponse> listUser(Pagination<ListUserRequest> request) {
        return publicLinkServiceTemplate.execute(new ProcessCallback<Pagination<ListUserRequest>, ListUserResponse>() {
            @Override
            public Pagination<ListUserRequest> getRequest() {
                return request;
            }

            @Override
            public void preProcess(Pagination<ListUserRequest> request) {
                Assert.notNull(getRequest(), "Pagination request cannot be null");
                Assert.notEmpty(getRequest().getListData(), "ListUserRequest cannot be empty");
            }

            @Override
            public ListUserResponse process() {
                List<User> users = coreUserService.getAllUser();
                List<GetUserResponse> userResponses = users.stream()
                        .map(this::toGetUserResponse)
                        .collect(Collectors.toList());
                ListUserResponse response = new ListUserResponse();
                response.setTotal(userResponses.size());
                response.setListUser(userResponses);
                return response;
            }

            private GetUserResponse toGetUserResponse(User user) {
                GetUserResponse resp = new GetUserResponse();
                resp.setUsername(user.getUserName());
                resp.setName(user.getName());
                resp.setEmail(null);
                resp.setRole(user.getType());
                return resp;
            }
        });
    }

    @Override
    public Mono<ResponseEntity<SellerRegisterResponse>> registerNewSeller(SellerRegisterRequest request) {
        SellerDTO sellerDTO = new SellerDTO();
        sellerDTO.setUsername(request.getUsername());
        sellerDTO.setLink(request.getProfileLink());
        sellerDTO.setName(request.getName());
        final String requestUuid = request.getReqUuid();
        Assert.isTrue(StringUtils.isNotBlank(requestUuid), String.format("Request uuid is required. Request uuid: %s", requestUuid));
        return operatedUserClient.findUserByUsername(new FindUserRequest(sellerDTO.getUsername()))
                .flatMap(findUserResponse -> {
                    // user exists in user-service → reject
                    SellerRegisterResponse conflict = new SellerRegisterResponse();
                    conflict.setMessage("User already exists");
                    conflict.setReqUuid(requestUuid);
                    return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).<SellerRegisterResponse>body(conflict));
                })
                .switchIfEmpty(Mono.defer(() -> {
                    try {
                        User seller = coreUserService.findSeller(sellerDTO);
                        Assert.isNull(seller, "User already exists in internal Database");
                        return createNewSellerUser(sellerDTO, requestUuid);
                    } catch (IllegalArgumentException e) {
                        SellerRegisterResponse response = new SellerRegisterResponse();
                        response.setMessage(e.getMessage());
                        response.setReqUuid(requestUuid);
                        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(response));
                    }
                }));
    }

    @Override
    public Result<GetUserResponse> getUserInfo(GetSellerRequest getSellerRequest) {
        return
                publicLinkServiceTemplate.execute(new ProcessCallback<GetSellerRequest, GetUserResponse>() {
                    @Override
                    public GetSellerRequest getRequest() {
                        return getSellerRequest;
                    }

                    @Override
                    public void preProcess(GetSellerRequest request) {
                        // 0. Validate request's properties
                        Assert.notNull(getSellerRequest, "GetSellerRequest cannot be null");
                        Assert.hasText(getSellerRequest.getUsername(), "Username is required");
                    }

                    @Override
                    public GetUserResponse process() {
                        // 1. Convert getSellerRequest to sellerDTO
                        SellerDTO sellerDTO = new SellerDTO();
                        sellerDTO.setUsername(getSellerRequest.getUsername());

                        // 2. Pass to findSeller as argument
                        User user = coreUserService.findSeller(sellerDTO);

                        if (user == null) {
                            throw new ResourceNotFoundException("User not found: " + sellerDTO.getUsername());
                        }

                        // Build response from User entity
                        GetUserResponse response = new GetUserResponse();
                        response.setUsername(user.getUserName());
                        response.setName(user.getName());
                        response.setEmail(null);
                        response.setRole(user.getType());

                        return response;
                    }
                });


    }

    public Mono<? extends ResponseEntity<SellerRegisterResponse>> createNewSellerUser(SellerDTO sellerDTO, String requestUuid) {
        UserRegisterRequest registerRequest = toUserRegisterRequest(sellerDTO);

        return operatedUserClient.createUser(registerRequest)
                .flatMap(result -> {
                    if (!result.isSuccess()) {
                        String errMsg = result.getErrorMessage() != null ? result.getErrorMessage() : "Unknown error from user service";
                        log.error("User service rejected create for [{}]: {}", sellerDTO.getUsername(), errMsg);
                        SellerRegisterResponse response = new SellerRegisterResponse();
                        response.setReqUuid(requestUuid);
                        response.setMessage(errMsg);
                        return Mono.just(ResponseEntity.badRequest().<SellerRegisterResponse>body(response));
                    }
                    sellerDTO.setUserType(UserType.SELLER);
                    return Mono.fromCallable(() -> coreUserService.createNewUser(sellerDTO))
                            .subscribeOn(Schedulers.boundedElastic())
                            .map(userModel -> {
                                SellerRegisterResponse response = buildSellerRegisterResponse(requestUuid, result.getData().getPassword(), userModel);
                                return ResponseEntity.<SellerRegisterResponse>ok(response);
                            })
                            .onErrorResume(ex -> {
                                log.error("Local DB create failed for [{}], compensating remote creation: {}",
                                        sellerDTO.getUsername(), ex.getMessage());
                                UserRemoveRequest deleteRequest = new UserRemoveRequest();
                                deleteRequest.setUserId(result.getData().getUserName());
                                return operatedUserClient.deleteUser(deleteRequest)
                                        .doOnSuccess(r -> log.info("Compensation succeeded for [{}]", sellerDTO.getUsername()))
                                        .doOnError(compEx -> log.error("Compensation failed for [{}]: {}", sellerDTO.getUsername(), compEx.getMessage()))
                                        .then(Mono.<ResponseEntity<SellerRegisterResponse>>error(ex));
                            });
                });
    }

    private SellerRegisterResponse buildSellerRegisterResponse(String requestUuid, String password, UserModel userModel) {
        SellerRegisterResponse response = new SellerRegisterResponse();
        response.setReqUuid(requestUuid);
        response.setUsername(userModel.getUsername());
        response.setMaskedPassword(password);
        response.setName(userModel.getName());
        response.setProfileLink(userModel.getLink());
        response.setMessage("Seller registered successfully");
        return response;
    }

    private UserRegisterRequest toUserRegisterRequest(SellerDTO sellerDTO) {
        ProfileLink profileLink = new ProfileLink();
        profileLink.setLink(sellerDTO.getLink());
        UserRegisterRequest req = new UserRegisterRequest();
        req.setUserName(sellerDTO.getUsername());
        req.setFullName(sellerDTO.getName());
        req.setRole(UserType.SELLER.name());
        req.setProfileLink(profileLink);
        return req;
    }
}
