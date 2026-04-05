package com.qrpublic.apartment.authenAuthorisation.user.repo;

import com.qrpublic.apartment.authenAuthorisation.user.dataEntity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository("authenUserRepository")
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
