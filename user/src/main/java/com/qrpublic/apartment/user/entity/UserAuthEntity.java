package com.qrpublic.apartment.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;

@Entity
@Table(name = "user_auth_tbl")
@Getter
@Setter
@NoArgsConstructor
public class UserAuthEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int authId;

    private String authToken;

    @Column(updatable = false, insertable = false)
    private Timestamp createdAt;
    private Timestamp expireAt;

    private Boolean isActive;

    @ManyToOne()
    @JoinColumn(name = "user_id")
    private UserEntity user;

    private int extendedNum;
}
