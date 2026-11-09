package com.qrpublic.apartment.databackup.repository;

import com.qrpublic.apartment.databackup.entity.OtpSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OtpSessionRepository extends JpaRepository<OtpSessionEntity, String> {
}
