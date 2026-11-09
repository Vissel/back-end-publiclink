package com.qrpublic.apartment.chat.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;

/**
 * Browser/device identity for a chat client. A device is recognised on return
 * visits by its persisted device id, and — when local storage was wiped — by a
 * similarity match over fingerprint / hashed IP.
 */
@Entity
@Table(name = "chat_device")
@Getter
@Setter
@NoArgsConstructor
public class ChatDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    @Column(name = "fingerprint", length = 128)
    private String fingerprint;

    @Column(name = "ip_hash", length = 64)
    private String ipHash;

    @Column(name = "user_agent", length = 300)
    private String userAgent;

    @Column(name = "visit_count", nullable = false)
    private Integer visitCount;

    @Column(name = "first_seen", updatable = false)
    private Timestamp firstSeen;

    @Column(name = "last_seen")
    private Timestamp lastSeen;
}
