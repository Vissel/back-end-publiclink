package com.qrpublic.apartment.model;

import com.qrpublic.apartment.entity.User;
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

    /**
     * considering unuse
     *
     * @param seller
     */
    public SellerDTO(User seller) {
        this.username = seller.getName();
        this.link = seller.getLink();
        this.name = seller.getName();
    }
}
