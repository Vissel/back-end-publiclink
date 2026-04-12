package com.qrpublic.apartment.user.repository;

import com.qrpublic.apartment.user.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserEntityRepository extends JpaRepository<UserEntity, String> {
    /**
     * name and link are unique constraint
     *
     * @param name
     * @param link
     * @return
     */
    Optional<UserEntity> findByNameAndLink(String name, String link);

    Optional<UserEntity> findByUserName(String username);
}
