package com.qrpublic.apartment.service;

import com.qrpublic.apartment.entity.User;
import com.qrpublic.apartment.model.SellerDTO;
import com.qrpublic.apartment.requestmodel.RegisterUserDTO;

public interface UserService {

    boolean saveAdminUser(RegisterUserDTO userDTO);

    boolean saveUser(RegisterUserDTO userDTO);

    User createSeller(SellerDTO seller);

    /**
     * Find user by username, or null if not found
     *
     * @param userName
     * @return
     */
    boolean checkAuthentedUserExist(String userName);
}
