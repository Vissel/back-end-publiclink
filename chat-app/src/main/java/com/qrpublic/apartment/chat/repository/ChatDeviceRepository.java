package com.qrpublic.apartment.chat.repository;

import com.qrpublic.apartment.chat.entity.ChatDevice;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatDeviceRepository extends JpaRepository<ChatDevice, Long> {

    Optional<ChatDevice> findByDeviceId(String deviceId);

    List<ChatDevice> findByFingerprintOrderByLastSeenDesc(String fingerprint);

    List<ChatDevice> findByIpHashOrderByLastSeenDesc(String ipHash);

    /**
     * Candidate set for fuzzy re-identification when the stored device id is gone.
     */
    List<ChatDevice> findByFingerprintIsNotNullOrderByLastSeenDesc(Pageable pageable);
}
