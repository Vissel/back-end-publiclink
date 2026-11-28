package com.qrpublic.apartment.comment.response;

import lombok.Data;

/** Cached author avatar for the comment icon; null dataUrl means "use initials". */
@Data
public class AvatarResponse {
    private String username;
    private String dataUrl;
}
