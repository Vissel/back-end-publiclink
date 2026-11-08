package com.qrpublic.apartment.service;

import com.qrpublic.apartment.constant.CommonConstant;
import com.qrpublic.apartment.core.model.AuthenticationEnum;
import com.qrpublic.apartment.core.model.UserModel;
import com.qrpublic.apartment.core.service.CoreUserService;
import com.qrpublic.apartment.entity.User;
import com.qrpublic.apartment.model.SellerDTO;
import com.qrpublic.apartment.repository.UserRepository;
import com.qrpublic.apartment.service.generating.model.RoleEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepo;

    @Autowired
    CoreUserService coreUserService;

    @Override
    public User createSeller(SellerDTO seller) {
        return Optional.ofNullable(coreUserService.findSeller(seller))
                .orElseGet(() -> {
                    User newUser = new User(seller.getUsername(), CommonConstant.EMPTY, seller.getUsername(),
                            seller.getLink(),
                            RoleEnum.SELLER.getRole());
                    return userRepo.save(newUser);
                });
    }

    @Override
    public boolean checkAuthentedUserExist(String userName) {
        UserModel userModel = coreUserService.findByUsername(userName);
        if (userModel != null && !AuthenticationEnum.UNAUTHENTICATED.equals(userModel.getAuthenticationEnum())) {
            return true;
        }
        return false;
    }

}
