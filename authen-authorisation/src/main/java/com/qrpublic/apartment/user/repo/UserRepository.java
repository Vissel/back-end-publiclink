package com.qrpublic.apartment.user.repo;

import com.qrpublic.apartment.user.dataEntity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    /**
     * name and link are unique constraint
     *
     * @param name
     * @param link
     * @return
     */
    Optional<User> findByNameAndLink(String name, String link);

    Optional<User> findByUserName(String username);
}
