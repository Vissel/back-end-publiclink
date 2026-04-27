package com.qrpublic.apartment.repository;

import com.qrpublic.apartment.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
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

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT u FROM User u")
    List<User> findAllWithLock();
}
