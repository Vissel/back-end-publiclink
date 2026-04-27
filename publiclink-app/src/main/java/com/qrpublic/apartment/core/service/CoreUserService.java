package com.qrpublic.apartment.core.service;

import com.qrpublic.apartment.core.model.UserModel;
import com.qrpublic.apartment.entity.User;
import com.qrpublic.apartment.model.SellerDTO;
import com.qrpublic.apartment.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class CoreUserService {
    @Autowired
    UserRepository userRepo;

    /**
     * Find seller by username and link, if not found, find by username only
     *
     * @param sellerDTO
     * @return
     */
    @Transactional
    public User findSeller(SellerDTO sellerDTO) {
        // Find with username and link
        Optional<User> user = userRepo.findByNameAndLink(sellerDTO.getUsername(), sellerDTO.getLink());
        if (!user.isPresent()) {
            // find with username
            user = userRepo.findByUserName(sellerDTO.getUsername());
        }
        return user.orElse(null);
    }

    @Transactional
    public List<User> getAllUser() {
        return userRepo.findAllWithLock();
    }

    @Transactional
    public UserModel createNewUser(SellerDTO sellerDTO) {
        User user = new User();
        user.setUserName(sellerDTO.getUsername());
        user.setLink(sellerDTO.getLink());
        user.setName(sellerDTO.getName());
        user.setType(sellerDTO.getUserType().name());
        userRepo.save(user);

        return UserModel.builder()
                .username(user.getUserName())
                .link(user.getLink())
                .name(user.getName())
                .type(user.getType())
                .build();
    }
}
