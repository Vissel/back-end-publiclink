package com.qrpublic.apartment.chat.repository;

import com.qrpublic.apartment.chat.entity.ChatConversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatConversationRepository extends JpaRepository<ChatConversation, Long> {

    Optional<ChatConversation> findByUuid(String uuid);

    /** Newest conversation carrying this short label shown in the Zalo thread. */
    Optional<ChatConversation> findTop1ByClientTagOrderByIdDesc(String clientTag);

    List<ChatConversation> findByDeviceIdOrderByIdDesc(String deviceId);

    /**
     * Newest thread of a signed-in account. Account threads are resolved by
     * owner alone: the device id is shared by every account on one browser.
     */
    Optional<ChatConversation> findTop1ByOwnerUsernameOrderByIdDesc(String ownerUsername);

    /**
     * Newest anonymous thread of a device. The owner-null filter is what keeps
     * a signed-out browser from inheriting an account's conversation.
     */
    Optional<ChatConversation> findTop1ByDeviceIdAndOwnerUsernameIsNullOrderByIdDesc(String deviceId);

    /**
     * True when some signed-in account has chatted on this device. Anonymous
     * fingerprint matching must skip such devices: adopting one would let a
     * signed-out visitor latch onto an account's device row and pollute its
     * fingerprint, IP hash and visit counter.
     */
    boolean existsByDeviceIdAndOwnerUsernameIsNotNull(String deviceId);

    Page<ChatConversation> findByStatusOrderByLastMessageAtDesc(String status, Pageable pageable);

    Page<ChatConversation> findByStatusIsNotNullOrderByLastMessageAtDesc(Pageable pageable);

    long countByStatus(String status);
}
