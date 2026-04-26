package com.qrpublic.apartment.core.service;

import com.qrpublic.apartment.entity.User;
import com.qrpublic.apartment.repository.UserRepository;
import com.qrpublic.apartment.requestmodel.SellerDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
