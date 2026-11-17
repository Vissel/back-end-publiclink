package com.qrpublic.apartment.transferinfo.request;

import lombok.Data;

/**
 * Body for setDefault / remove operations, referenced by transferCode.
 */
@Data
public class TransferCodeRequest {
    private String transferCode;
}
