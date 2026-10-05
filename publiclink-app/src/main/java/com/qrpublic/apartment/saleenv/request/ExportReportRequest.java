package com.qrpublic.apartment.saleenv.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExportReportRequest {

    private String requestUUID;

    /** Frontend i18n language code, e.g. {@code en} or {@code vi}. */
    private String locale;

}
