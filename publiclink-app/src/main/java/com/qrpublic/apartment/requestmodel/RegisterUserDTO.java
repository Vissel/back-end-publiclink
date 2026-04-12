package com.qrpublic.apartment.requestmodel;

import com.qrpublic.apartment.service.generating.model.RoleEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegisterUserDTO {

    private String userId;
    private String userName;
    private String password;
    private String name;
    private String link;
    private RoleEnum role;
}
