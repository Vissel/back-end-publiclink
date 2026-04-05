package com.qrpublic.apartment.service;

import com.qrpublic.apartment.authenAuthorisation.authorisation.model.RoleEnum;
import com.qrpublic.apartment.constant.CommonConstant;
import com.qrpublic.apartment.entity.User;
import com.qrpublic.apartment.repository.UserRepository;
import com.qrpublic.apartment.requestmodel.RegisterUserDTO;
import com.qrpublic.apartment.requestmodel.SellerDTO;
import com.qrpublic.apartment.util.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private UserRepository userRepo;

    @Override
    public boolean saveAdminUser(RegisterUserDTO userDTO) {
        boolean isSaved = false;
        if (userDTO.getUserId() == null) {
            Optional<User> existedUser = userRepo.findByUserName(userDTO.getUserName());

            User user;
            if (!existedUser.isPresent()) {
                user = new User(userDTO.getUserName(), encoder.encode(userDTO.getPassword()), userDTO.getName(),
                        userDTO.getLink(), userDTO.getRole().getRole());
                isSaved = userRepo.save(user).getUserId() != null;
            }
        }
        return isSaved;
    }

    @Override
    public boolean saveUser(RegisterUserDTO userDTO) {
        Optional<User> existedUser = userRepo.findById(userDTO.getUserId());
        User user;
        if (existedUser.isPresent()) {
            user = existedUser.get();
            saveUpdatedDTO(userDTO, user);
        } else {
            user = new User(userDTO.getUserName(), encoder.encode(userDTO.getPassword()), userDTO.getName(),
                    userDTO.getLink(), userDTO.getRole().getRole());
        }
        return userRepo.save(user).getUserId() != null;
    }

    private void saveUpdatedDTO(RegisterUserDTO userDTO, User user) {
        if (Utils.isValidStr(userDTO.getUserName()) && !userDTO.getUserName().equals(user.getUserName())) {
            user.setUserName(userDTO.getUserName());
        }
        if (Utils.isValidStr(userDTO.getPassword())
                && !encoder.matches(userDTO.getPassword(), user.getTempPassword())) {
            user.setTempPassword(encoder.encode(userDTO.getPassword()));
        }
        if (Utils.isValidStr(userDTO.getName()) && !userDTO.getName().equals(user.getName())) {
            user.setName(userDTO.getName());
        }
        if (Utils.isValidStr(userDTO.getLink()) && !userDTO.getLink().equals(user.getLink())) {
            user.setLink(userDTO.getLink());
        }
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
                sellerU = new User(seller.getUsername(), CommonConstant.EMPTY, seller.getUsername(), seller.getLink(),
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
