package com.qrpublic.apartment.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;
import java.util.List;

@Entity
@Table(name = "user_tbl")
@Getter
@Setter
@NoArgsConstructor
public class UserEntity {
    @Id
    private String userId;

    private String username;
    private String password;
    private String fullName;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ProfileEntity> profiles;
    private String role;
    private Boolean isActive;

    @Column(updatable = false, insertable = false)
    private Timestamp createdAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<UserAuthEntity> userAuthentication;

    @Version
    private long version;
}
