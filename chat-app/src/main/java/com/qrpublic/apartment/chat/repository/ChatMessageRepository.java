package com.qrpublic.apartment.chat.repository;

import com.qrpublic.apartment.chat.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("SELECT m FROM ChatMessage m JOIN m.conversation c WHERE c.id = :conversationId ORDER BY m.id ASC")
    List<ChatMessage> listByConversation(@Param("conversationId") Long conversationId);

    /**
     * Replay window for a reconnecting client: everything after the last seen id.
     */
    @Query("SELECT m FROM ChatMessage m JOIN m.conversation c WHERE c.id = :conversationId "
            + "AND m.id > :afterId ORDER BY m.id ASC")
    List<ChatMessage> replayAfter(@Param("conversationId") Long conversationId,
            @Param("afterId") Long afterId);

    Optional<ChatMessage> findByZaloMessageId(String zaloMessageId);

    Optional<ChatMessage> findByClientMessageId(String clientMessageId);

    @Query("SELECT m FROM ChatMessage m JOIN m.conversation c WHERE c.id = :conversationId ORDER BY m.id DESC")
    List<ChatMessage> findLastOfConversation(@Param("conversationId") Long conversationId, Pageable pageable);

    @Query("SELECT m FROM ChatMessage m WHERE m.direction = :direction ORDER BY m.id DESC")
    List<ChatMessage> findRecentByDirection(@Param("direction") String direction, Pageable pageable);

    /**
     * Client messages already dispatched to Zalo, newest first — used to resolve a
     * Zalo seen/received event that carries only partial message identification.
     */
    @Query("SELECT m FROM ChatMessage m WHERE m.direction = :direction "
            + "AND m.zaloMessageId IS NOT NULL ORDER BY m.id DESC")
    List<ChatMessage> findDispatchedMessages(@Param("direction") String direction, Pageable pageable);

    @Query("SELECT COUNT(m) FROM ChatMessage m JOIN m.conversation c WHERE c.id = :conversationId "
            + "AND m.direction = :direction")
    long countByConversationAndDirection(@Param("conversationId") Long conversationId,
            @Param("direction") String direction);
}
