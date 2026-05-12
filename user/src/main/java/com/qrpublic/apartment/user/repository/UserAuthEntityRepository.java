package com.qrpublic.apartment.user.repository;

import com.qrpublic.apartment.user.entity.UserAuthEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface UserAuthEntityRepository extends JpaRepository<UserAuthEntity, Integer> {
    /**
     * Find user auth by username
     *
     * @param username
     * @return
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT u FROM UserAuthEntity u WHERE u.userEntity.username = :username")
    List<UserAuthEntity> findByUsername(String username);


}
