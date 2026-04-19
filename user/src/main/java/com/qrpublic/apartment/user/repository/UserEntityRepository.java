package com.qrpublic.apartment.user.repository;

import com.qrpublic.apartment.user.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserEntityRepository extends JpaRepository<UserEntity, String> {
    /**
     * Find user by username
     *
     * @param username
     * @return
     */
    Optional<UserEntity> findByUsername(String username);
}
