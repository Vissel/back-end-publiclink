package com.qrpublic.apartment.core.model;

import lombok.Data;

import java.sql.Timestamp;

/**
 * Core-layer view of a recognised client device.
 */
@Data
public class ChatDeviceModel {
    private Long id;
    private String deviceId;
    private String fingerprint;
    private String ipHash;
    private String userAgent;
    private Integer visitCount;
    private Timestamp firstSeen;
    private Timestamp lastSeen;
    /** How this visit was tied to the stored device: NEW / DEVICE_ID / FINGERPRINT / IP. */
    private String recognition;
}
