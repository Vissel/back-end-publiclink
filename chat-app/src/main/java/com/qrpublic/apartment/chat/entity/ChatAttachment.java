package com.qrpublic.apartment.chat.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;

/**
 * Metadata of the one binary payload a chat message may carry (an image or a
 * standard document). The bytes live on disk under {@code chat.upload-dir}
 * named by {@code storageKey}; this row is what both clients render and what
 * the download endpoints authorise against.
 */
@Entity
@Table(name = "chat_attachment")
@Getter
@Setter
@NoArgsConstructor
public class ChatAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unguessable external id used in download URLs. */
    @Column(name = "uuid", nullable = false, length = 36, unique = true)
    private String uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private ChatMessage message;

    /** IMAGE (inline thumbnail) / FILE (download row). */
    @Column(name = "kind", nullable = false, length = 10)
    private String kind;

    /** Sender's original name, sanitised; display only. */
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    /** File name on disk (uuid + extension), never the user-supplied name. */
    @Column(name = "storage_key", nullable = false, length = 64)
    private String storageKey;

    @Column(name = "created_at", updatable = false)
    private Timestamp createdAt;
}
