package com.qrpublic.apartment.service;

import com.qrpublic.apartment.entity.User;
import com.qrpublic.apartment.requestmodel.RegisterUserDTO;
import com.qrpublic.apartment.requestmodel.SellerDTO;

public interface UserService {

	boolean saveAdminUser(RegisterUserDTO userDTO);

    boolean saveUser(RegisterUserDTO userDTO);

	User createSeller(SellerDTO seller);

	User findByUserName(String userName);
}
