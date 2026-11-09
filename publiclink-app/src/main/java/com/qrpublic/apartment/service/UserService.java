package com.qrpublic.apartment.service;

import com.qrpublic.apartment.entity.User;
import com.qrpublic.apartment.model.SellerDTO;

public interface UserService {

    User createSeller(SellerDTO seller);

    /**
     * Find user by username, or null if not found
     *
     * @param userName
     * @return
     */
    boolean checkAuthentedUserExist(String userName);
}
