package com.qrpublic.apartment.msgbroker.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class MessageEvent {
    private String messageId;
    private String transactionId;
    private BigDecimal amount;
    private String accountNumber;
    private LocalDateTime timestamp;
}
