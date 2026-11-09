package com.qrpublic.apartment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;

/**
 * Raw Zalo webhook event, written before any processing. Zalo retries a
 * non-acknowledged event up to five times, so the unique event id doubles as the
 * idempotency guard and processed=false rows can be re-driven by a scheduler.
 */
@Entity
@Table(name = "zalo_event_log")
@Getter
@Setter
@NoArgsConstructor
public class ZaloEventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "event_name", length = 64)
    private String eventName;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "processed", nullable = false)
    private boolean processed;

    @Column(name = "created_at", updatable = false)
    private Timestamp createdAt;
}
