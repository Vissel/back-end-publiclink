package com.qrpublic.apartment.core.model;

import lombok.Data;

/**
 * Core-layer view of a persisted Zalo credential/binding row.
 */
@Data
public class ZaloCredentialModel {
    private Long id;
    private String scopeKey;
    private String valueA;
    private String valueB;
    private Long expiresAt;
}
