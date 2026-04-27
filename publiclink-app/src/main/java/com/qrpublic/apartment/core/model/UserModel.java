package com.qrpublic.apartment.core.model;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class UserModel {
    private String username;
    @Deprecated
    private String link;
    private String name;
    private String type;

    public UserModel() {
    }
}
