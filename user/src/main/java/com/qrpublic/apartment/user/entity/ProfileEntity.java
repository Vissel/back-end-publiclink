package com.qrpublic.apartment.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "profile_tbl")
@Getter
@Setter
@NoArgsConstructor
public class ProfileEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int profileId;

    private String profileLink;

    private String profileType;

    @ManyToOne
    private UserEntity user;
}
