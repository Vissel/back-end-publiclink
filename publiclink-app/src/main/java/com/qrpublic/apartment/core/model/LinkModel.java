package com.qrpublic.apartment.core.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Date;

@Data
@AllArgsConstructor
public class LinkModel {
    private String link;
    private String requestUuid;
    private String authLink;
    private Date issueAt;
    private Date expire;


}
