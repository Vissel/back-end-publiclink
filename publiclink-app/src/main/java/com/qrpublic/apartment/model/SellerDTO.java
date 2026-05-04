package com.qrpublic.apartment.model;

import com.qrpublic.apartment.requestmodel.AuthToken;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SellerDTO extends AuthToken {
    private String username;
    private String link;
    private String productName;
    private String name;
    private UserType userType;

}
