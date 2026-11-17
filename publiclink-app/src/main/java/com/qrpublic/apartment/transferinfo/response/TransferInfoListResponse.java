package com.qrpublic.apartment.transferinfo.response;

import lombok.Data;

import java.util.List;

/**
 * Transfer accounts of the caller, default first.
 */
@Data
public class TransferInfoListResponse {

    private List<TransferItem> items;

    @Data
    public static class TransferItem {
        private String transferCode;
        private String transferIssuer;
        private String issuerFullName;
        private String type;
        private String transferNumber;
        private String accountName;
        private String qrImage;
        private String defaultMessage;
        private String status;
        private boolean isDefault;
    }
}
