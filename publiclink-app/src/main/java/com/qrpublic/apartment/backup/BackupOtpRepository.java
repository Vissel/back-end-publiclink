package com.qrpublic.apartment.backup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BackupOtpRepository extends JpaRepository<BackupOtp, String> {}
