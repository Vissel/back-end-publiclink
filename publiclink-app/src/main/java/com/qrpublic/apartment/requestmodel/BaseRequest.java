package com.qrpublic.apartment.requestmodel;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BaseRequest {
    @NotNull
    private String reqUuid;
}
