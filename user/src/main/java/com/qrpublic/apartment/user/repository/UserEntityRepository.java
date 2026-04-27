package com.qrpublic.apartment.user.repository;

import com.qrpublic.apartment.user.entity.UserEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface UserEntityRepository extends JpaRepository<UserEntity, String> {
    /**
     * Find user by username
     *
     * @param username
     * @return
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserEntity> findByUsernameForUpdate(String username);

    Optional<UserEntity> findByUsername(String username);

}
