package com.qrpublic.apartment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;
import java.time.LocalTime;

/**
 * Weekly recurring yoga class owned by one sale environment. day_of_week is
 * ISO (1 = Monday .. 7 = Sunday); start/end are 24h clock times. capacity is
 * the slot count of every weekly occurrence.
 */
@Entity
@Table(name = "yoga_class")
@Getter
@Setter
@NoArgsConstructor
public class YogaClass {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "class_id")
    private Long classId;

    @Column(name = "class_code", nullable = false, unique = true, length = 36)
    private String classCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "env_id", nullable = false)
    private SaleEnvironment saleEnvironment;

    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 100)
    private String coach;

    @Column(nullable = false)
    private int capacity = 1;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", updatable = false, insertable = false)
    private Timestamp createdAt;

    @Column(name = "updated_at")
    private Timestamp updatedAt;
}
