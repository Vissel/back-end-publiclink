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

    /**
     * Contact email captured at self-registration; null for accounts the admin
     * created by hand.
     */
    private String email;

    /**
     * Which plan group this account was registered for: PRODUCT or BOOKING.
     * Null means the purpose was never asked, which reads as PRODUCT so every
     * pre-existing account keeps the catalog it already bought from.
     */
    @Column(name = "plan_purpose", length = 20)
    private String planPurpose;

    @Column(updatable = false, insertable = false)
    private Timestamp createdAt;

    private String authenticationMethod;

    public User(String username, String pass, String name, String link, String type) {
        this.userName = username;
        this.tempPassword = pass;
        this.name = name;
        this.link = link;
        this.type = type;
    }
}
