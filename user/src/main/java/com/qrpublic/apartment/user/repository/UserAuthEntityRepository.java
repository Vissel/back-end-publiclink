package com.qrpublic.apartment.user.repository;

import com.qrpublic.apartment.user.entity.UserAuthEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface UserAuthEntityRepository extends JpaRepository<UserAuthEntity, Integer> {
    /**
     * Find user auth by username
     *
     * @param username
     * @return
     */
    @Query("SELECT u FROM UserAuthEntity u WHERE u.user.username = :username")
    List<UserAuthEntity> findByUsername(String username);


}
