package com.qrpublic.apartment.repository;

import com.qrpublic.apartment.entity.PasswordSetupToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasswordSetupTokenRepository extends JpaRepository<PasswordSetupToken, Long> {

        Optional<PasswordSetupToken> findByTokenCode(String tokenCode);

        /**
         * Lock the row while validating + consuming so two submits on the same
         * link cannot both pass.
         */
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT t FROM PasswordSetupToken t WHERE t.tokenCode = :tokenCode")
        Optional<PasswordSetupToken> findByTokenCodeForUpdate(@Param("tokenCode") String tokenCode);
}
