package com.qrpublic.apartment.user.service;

import com.qrpublic.apartment.adapter.template.Result;
import com.qrpublic.apartment.user.repository.UserEntityRepository;
import com.qrpublic.apartment.user.service.response.FoundUserResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {
    @Autowired
    UserEntityRepository userEntityRepository;

    @Override
    public Result<FoundUserResponse> findByUserName(String userName) {
        return userEntityRepository.findByUserName(userName)
                .map(userEntity -> {
                    FoundUserResponse response = new FoundUserResponse();
                    response.setUserName(userEntity.getUserName());
                    response.setName(userEntity.getName());
                    return Result.success(response);
                })
                .orElseGet(() -> Result.error(404, "User not found"));
    }
}
