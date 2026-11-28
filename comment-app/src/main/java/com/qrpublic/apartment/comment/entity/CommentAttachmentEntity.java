package com.qrpublic.apartment.comment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;

/**
 * Metadata of one image/file payload of a comment. The bytes live on disk
 * under {@code comment.upload-dir} named by {@code storageKey}; this row is
 * what clients render and what the public download endpoint serves (the
 * unguessable uuid is the capability, since comment reads are public).
 */
@Entity
@Table(name = "comment_attachment")
@Getter
@Setter
@NoArgsConstructor
public class CommentAttachmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unguessable external id used in download URLs. */
    @Column(name = "uuid", nullable = false, length = 36, unique = true)
    private String uuid;

    @Column(name = "comment_id", nullable = false)
    private Long commentId;

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
