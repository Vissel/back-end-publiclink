package com.qrpublic.apartment.service;

import com.qrpublic.apartment.entity.User;
import com.qrpublic.apartment.requestmodel.RegisterUserDTO;
import com.qrpublic.apartment.requestmodel.SellerDTO;

public interface UserService {
	public boolean saveUser(RegisterUserDTO userDTO);

	public User createSeller(SellerDTO seller);

	public User findByUserName(String userName);
}
