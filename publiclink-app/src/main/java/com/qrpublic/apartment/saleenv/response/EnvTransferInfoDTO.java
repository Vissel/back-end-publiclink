package com.qrpublic.apartment.saleenv.response;

import lombok.Data;

/**
 * The transfer account shown on the public sale page. Only what a buyer needs
 * to pay travels here: no owner, status or default flag.
 */
@Data
public class EnvTransferInfoDTO {
    private String transferCode;
    private String transferIssuer;
    private String issuerFullName;
    private String type;
    private String transferNumber;
    private String accountName;
    private String qrImage;
    private String defaultMessage;
}
