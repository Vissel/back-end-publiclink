package com.qrpublic.apartment.entity;


import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "process_message")
@Getter
@Setter
@NoArgsConstructor
public class ProcessMessage {
    @Id
    private String msgId;
    private Instant createdAt;
    private String queueName;
    private String status;

}
