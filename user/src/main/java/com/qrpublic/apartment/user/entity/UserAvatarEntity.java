package com.qrpublic.apartment.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;

/**
 * Profile picture of one account, stored inline as base64.
 *
 * The browser resizes the image to a 256x256 square before uploading it, so a
 * row stays a few tens of kilobytes and the system needs no object storage or
 * extra volume mount. The table is created by ddl-auto=update, which is this
 * module's existing migration mechanism (unlike publiclink-app and chat-app it
 * carries no Liquibase changelog).
 */
@Entity
@Table(name = "user_avatar")
@Getter
@Setter
@NoArgsConstructor
public class UserAvatarEntity {

    /** Primary key on purpose: one picture per account, no surrogate id. */
    @Id
    @Column(name = "username", length = 64)
    private String username;

    /** MIME type as stored, e.g. "image/png". */
    @Column(name = "content_type", length = 40)
    private String contentType;

    /** Base64 payload WITHOUT the "data:...;base64," prefix. */
    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "data", columnDefinition = "MEDIUMTEXT")
    private String data;

    @Column(name = "updated_at")
    private Timestamp updatedAt;
}
