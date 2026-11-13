package com.qrpublic.apartment.user.service.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Avatar upload. A blank {@code dataUrl} removes the stored picture, which is
 * how "Delete avatar" works in the account dialog.
 */
@Getter
@Setter
@NoArgsConstructor
public class AvatarUpdateRequest {

    private String dataUrl;
}
