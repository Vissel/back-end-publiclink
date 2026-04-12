package com.qrpublic.apartment.adapter.authentication.request;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FindUserRequest {
    private String userName;
}
