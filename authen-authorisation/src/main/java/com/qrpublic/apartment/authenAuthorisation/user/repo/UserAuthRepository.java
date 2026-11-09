package com.qrpublic.apartment.authenAuthorisation.user.repo;

import com.qrpublic.apartment.authenAuthorisation.user.dataEntity.User;

import java.util.Optional;

public interface UserAuthRepository {
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
