package com.qrpublic.apartment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;

@Entity
@Table(name = "user")
@Getter
@Setter
@NoArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String userId;

    private String userName;
    private String tempPassword;
    private String name;
    private String link;
    private String type;

    @Column(updatable = false, insertable = false)
    private Timestamp createdAt;

    public User(String username, String pass, String name, String link, String type) {
        this.userName = username;
        this.tempPassword = pass;
        this.name = name;
        this.link = link;
        this.type = type;
    }
}
