package com.qrpublic.apartment.service;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.qrpublic.apartment.constant.CommonConstant;
import com.qrpublic.apartment.entity.User;
import com.qrpublic.apartment.repository.UserRepository;
import com.qrpublic.apartment.requestmodel.RegisterUserDTO;
import com.qrpublic.apartment.requestmodel.RoleEnum;
import com.qrpublic.apartment.requestmodel.SellerDTO;

@Service
public class UserServiceImpl implements UserService {
	@Autowired
	private PasswordEncoder encoder;

	@Autowired
	private UserRepository userRepo;

	@Override
	public boolean saveUser(RegisterUserDTO userDTO) {
		User user = new User(userDTO.getUserName(), encoder.encode(userDTO.getPassword()), userDTO.getName(),
				userDTO.getRole().getRole());
		user.setLink(userDTO.getLink());
		return userRepo.save(user).getUserId() != null;
	}

	@Override
	public User createSeller(SellerDTO seller) {
		// Find with username and link
		Optional<User> user = userRepo.findByNameAndLink(seller.getUsername(), seller.getLink());
		if (!user.isPresent()) {
			// find with username
			user = userRepo.findByUserName(seller.getUsername());
			User sellerU;
			boolean isSaved = true;
			if (!user.isPresent()) {
				// create new user
				sellerU = new User(seller.getUsername(), CommonConstant.EMPTY, seller.getUsername(),
						RoleEnum.SELLER.getRole());
			} else {
				sellerU = user.get();
				if (seller.getLink() != null && !seller.getLink().isBlank()) {
					sellerU.setLink(seller.getLink());
				} else {
					isSaved = false;
				}
			}
			if (isSaved) {
				return userRepo.save(sellerU);
			}
		}
		return user.get();
	}

	@Override
	public User findByUserName(String userName) {
		return userRepo.findByUserName(userName).orElseThrow();
	}

}
