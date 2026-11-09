package com.qrpublic.apartment.chat.repository;

import com.qrpublic.apartment.chat.entity.ZaloCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ZaloCredentialRepository extends JpaRepository<ZaloCredential, Long> {

    Optional<ZaloCredential> findByScopeKey(String scopeKey);
}
