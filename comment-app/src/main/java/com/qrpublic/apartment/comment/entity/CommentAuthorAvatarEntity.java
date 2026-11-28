package com.qrpublic.apartment.comment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;

/**
 * Server-side cache of an author's avatar (base64 data URL copied from the
 * user service) plus the last resolved display name. It lets the public
 * avatar endpoint serve pictures without exposing any new public
 * user-service route.
 */
@Entity
@Table(name = "comment_author_avatar")
@Getter
@Setter
@NoArgsConstructor
public class CommentAuthorAvatarEntity {

    @Id
    @Column(name = "username", nullable = false, length = 100)
    private String username;

    /** Base64 data URL; null means "this author has no picture". */
    @Column(name = "data_url", columnDefinition = "MEDIUMTEXT")
    private String dataUrl;

    @Column(name = "full_name", length = 255)
    private String fullName;

    @Column(name = "fetched_at", nullable = false)
    private Timestamp fetchedAt;
}
