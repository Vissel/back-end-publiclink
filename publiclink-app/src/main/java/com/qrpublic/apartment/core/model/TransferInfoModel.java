package com.qrpublic.apartment.core.model;

import lombok.Builder;
import lombok.Data;

/**
 * Core layer view of a transfer info (bank / e-wallet account). External
 * identifier is transferCode; DB id is never exposed.
 */
@Data
@Builder
public class TransferInfoModel {
    private String transferCode;
    /** ADMIN | SELLER */
    private String ownerType;
    private String transferIssuer;
    private String issuerFullName;
    /** BANK | EWALLET | OTHER */
    private String type;
    private String transferNumber;
    private String accountName;
    private String qrImage;
    private String defaultMessage;
    /** ACTIVE | DEACTIVE */
    private String status;
    private boolean isDefault;
}
