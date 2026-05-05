package com.qrpublic.apartment.core.model;

import lombok.Data;

import java.util.Date;

@Data
public class SellerModel {

    private String username;
    private String profileLink;
    private String name;

    private String sellerProfileLink;
    /**
     * the link for seller while 1st time accessing sale environment
     */
    private String sellerAuthLink;
    /**
     * expired of seller authentication link
     */
    private Date sellerAuthLinkExpire;

}
