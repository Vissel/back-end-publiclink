package com.qrpublic.apartment.saleenv.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for exporting all seller reports.
 * Used with the two-step streaming export pattern:
 * 1. POST /exportAllToken → returns a download token
 * 2. GET /stream/exportAll/{token} → streams the Excel file
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExportAllRequest {

    private String sellerName;

}
