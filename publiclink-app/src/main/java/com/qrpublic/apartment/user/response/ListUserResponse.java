package com.qrpublic.apartment.user.response;

import lombok.Data;

import java.util.List;

@Data
public class ListUserResponse {
    private int total;
    private List<UserResponse> listUser;
}
