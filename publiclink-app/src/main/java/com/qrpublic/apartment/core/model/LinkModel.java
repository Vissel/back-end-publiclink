package com.qrpublic.apartment.core.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Date;

@Data
@AllArgsConstructor
public class LinkModel {
    private String contextString;
    private String token;
    private Date issueAt;
    private Date expire;

    public LinkModel(String token, Date issueAt, Date expire) {
        this.token = token;
        this.issueAt = issueAt;
        this.expire = expire;
    }

}
