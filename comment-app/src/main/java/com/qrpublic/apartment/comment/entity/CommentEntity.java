package com.qrpublic.apartment.comment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;

/**
 * One node of a comment tree. Tree navigation deliberately uses plain id
 * columns (parentId / rootId) instead of object references: the whole thread is
 * fetched by root id in one query, so no lazy graph traversal is ever needed.
 *
 * lastActivityAt is meaningful ONLY on root rows (it is the bump key of the
 * listing: a reply refreshes it on the owning root, lifting the tree to the
 * top). Non-root rows keep it equal to createdAt and nobody reads it there.
 */
@Entity
@Table(name = "comment")
@Getter
@Setter
@NoArgsConstructor
public class CommentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unguessable external id used in URLs. */
    @Column(name = "uuid", nullable = false, length = 36, unique = true)
    private String uuid;

    /** PRODUCT / POST / GUIDELINE */
    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    @Column(name = "target_id", nullable = false, length = 64)
    private String targetId;

    /** Direct parent, null for a root comment (any depth is storable). */
    @Column(name = "parent_id")
    private Long parentId;

    /** Owning root comment; equal to id for a root. Set right after insert. */
    @Column(name = "root_id")
    private Long rootId;

    /** From the gateway-forwarded X-User-ID only; never from the body. */
    @Column(name = "author_username", nullable = false, length = 100)
    private String authorUsername;

    /** Snapshot of the author's name at write time (hover tooltip content). */
    @Column(name = "author_display_name", length = 255)
    private String authorDisplayName;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    @Column(name = "edited_at")
    private Timestamp editedAt;

    /** Insert-only: written by the application, readable right after save. */
    @Column(name = "created_at", updatable = false)
    private Timestamp createdAt;

    @Column(name = "last_activity_at")
    private Timestamp lastActivityAt;
}
