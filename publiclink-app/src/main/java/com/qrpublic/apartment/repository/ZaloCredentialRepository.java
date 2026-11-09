package com.qrpublic.apartment.repository;

import com.qrpublic.apartment.entity.ZaloCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ZaloCredentialRepository extends JpaRepository<ZaloCredential, Long> {

    Optional<ZaloCredential> findByScopeKey(String scopeKey);
}
