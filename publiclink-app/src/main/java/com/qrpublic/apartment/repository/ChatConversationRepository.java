package com.qrpublic.apartment.repository;

import com.qrpublic.apartment.entity.ChatConversation;
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

    Page<ChatConversation> findByStatusOrderByLastMessageAtDesc(String status, Pageable pageable);

    Page<ChatConversation> findByStatusIsNotNullOrderByLastMessageAtDesc(Pageable pageable);

    long countByStatus(String status);
}
