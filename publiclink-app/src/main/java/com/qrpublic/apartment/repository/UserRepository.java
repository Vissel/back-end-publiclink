package com.qrpublic.apartment.repository;

import com.qrpublic.apartment.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    /**
     * name and link are unique constraint
     *
     * @param name
     * @param link
     * @return
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<User> findByNameAndLink(String name, String link);

    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<User> findByUserName(String username);
}
