package com.qrpublic.apartment.repository;

import com.qrpublic.apartment.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT u FROM User u WHERE u.userId = :userId")
        User getUserForUpdate(String userId);

        @Query("SELECT u FROM User u " +
                        "WHERE (:userName IS NULL OR u.userName LIKE CONCAT('%', :userName, '%')) " +
                        "AND (:name IS NULL OR u.name LIKE CONCAT('%', :name, '%')) " +
                        "AND (:email IS NULL OR u.link LIKE CONCAT('%', :email, '%')) " +
                        "AND (:type IS NULL OR u.type LIKE CONCAT('%', :type, '%'))")
        Page<User> findByFilters(
                        @Param("userName") String userName,
                        @Param("name") String name,
                        @Param("email") String email,
                        @Param("type") String type,
                        Pageable pageable);

        @Query("SELECT u FROM User u WHERE u.type = 'SELLER' " +
                        "AND (:search IS NULL OR u.userName LIKE CONCAT('%', :search, '%') OR u.name LIKE CONCAT('%', :search, '%'))")
        Page<User> findSellers(@Param("search") String search, Pageable pageable);

        @Query("SELECT u FROM User u WHERE u.userName IN :usernames")
        List<User> findByUserNames(@Param("usernames") List<String> usernames);

}
