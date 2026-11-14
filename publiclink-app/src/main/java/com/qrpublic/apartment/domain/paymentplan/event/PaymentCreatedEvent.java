package com.qrpublic.apartment.domain.paymentplan.event;

import java.math.BigDecimal;

/**
 * Fired right after a seller plan assignment + payment are committed, so the
 * QR generation runs as a separate, decoupled step against the stored payment.
 *
 * @param paymentCode  external payment identifier the QR is linked to
 * @param amount       plan amount to encode in the QR
 * @param username     seller the plan was assigned to (part of the QR content)
 * @param planLabel    human readable plan descriptor (part of the QR content)
 * @param adminUsername admin whose default transfer account receives the money
 */
public record PaymentCreatedEvent(
        String paymentCode,
        BigDecimal amount,
        String username,
        String planLabel,
        String adminUsername) {
}
