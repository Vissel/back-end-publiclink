package com.qrpublic.apartment.user.service.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Own profile picture. {@code dataUrl} is null when no avatar was uploaded
 * yet, which the header renders as the initials fallback.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AvatarResponse {

    private String userName;

    /** Full "data:image/png;base64,..." URL, ready for an img src. */
    private String dataUrl;
}
