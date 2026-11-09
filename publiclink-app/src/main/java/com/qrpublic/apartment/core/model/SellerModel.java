package com.qrpublic.apartment.core.model;

import lombok.Data;

@Data
public class SellerModel {

    private String username;
    private String profileLink;
    private String name;

    private String sellerProfileLink;

    /**
     * link model
     */
    private LinkModel sellerLinkModel;
}
