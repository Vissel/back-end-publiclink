package com.qrpublic.apartment.user.core;

import com.qrpublic.apartment.user.entity.ProfileEntity;
import com.qrpublic.apartment.user.entity.UserEntity;
import com.qrpublic.apartment.user.model.User;
import com.qrpublic.apartment.user.repository.UserEntityRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserServiceCore {
    @Autowired
    UserEntityRepository userEntityRepository;

    @Transactional
    public User doFindUserByUsername(String username) {
        return userEntityRepository.findByUsername(username)
                .map(this::convertEntityToUser)
                .orElse(null);
    }

    private User convertEntityToUser(UserEntity entity) {
        User user = new User();
        user.setUsername(entity.getUsername());
        user.setPassword(entity.getPassword());
        user.setFullName(entity.getFullName());
        user.setRole(entity.getRole());
        user.setIsActive(entity.getIsActive());
        if (entity.getProfiles() != null && !entity.getProfiles().isEmpty()) {
            List<String> profileLinks = entity.getProfiles().stream()
                    .map(ProfileEntity::getProfileLink)
                    .toList();
            user.setProfileLinks(profileLinks);
        }
        return user;
    }
}
