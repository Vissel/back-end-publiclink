package com.qrpublic.apartment.saleenv.request;

import com.qrpublic.apartment.requestmodel.PubUserRequest;
import lombok.Data;

@Data
public class CreatedRequestIdRequest {
    private PubUserRequest sellerRequest;
}
