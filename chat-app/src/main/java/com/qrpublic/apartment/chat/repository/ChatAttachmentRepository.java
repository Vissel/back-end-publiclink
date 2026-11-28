package com.qrpublic.apartment.chat.repository;

import com.qrpublic.apartment.chat.entity.ChatAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatAttachmentRepository extends JpaRepository<ChatAttachment, Long> {

    Optional<ChatAttachment> findByUuid(String uuid);

    List<ChatAttachment> findByMessageIdIn(Collection<Long> messageIds);
}
