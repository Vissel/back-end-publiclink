package com.qrpublic.apartment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;

@Entity
@Table(name = "transfer_info")
@Getter
@Setter
@NoArgsConstructor
public class TransferInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transfer_info_id")
    private Long transferInfoId;

    @Column(name = "transfer_code", nullable = false, unique = true, length = 36)
    private String transferCode;

    /**
     * Owner is stored as a username: admins are managed by the user service and
     * have no row in publiclink-db.user, so this cannot be a foreign key.
     */
    @Column(name = "owner_username", nullable = false, length = 50)
    private String ownerUsername;

    /**
     * ADMIN | SELLER
     */
    @Column(name = "owner_type", nullable = false, length = 10)
    private String ownerType;

    @Column(name = "transfer_issuer", nullable = false, length = 100)
    private String transferIssuer;

    @Column(name = "issuer_full_name", length = 150)
    private String issuerFullName;

    /**
     * BANK | EWALLET | OTHER
     */
    @Column(name = "type", nullable = false, length = 20)
    private String type = "BANK";

    @Column(name = "transfer_number", nullable = false, length = 50)
    private String transferNumber;

    /**
     * NAPAS BIN of the chosen bank (e.g. 970436), used as the VietQR BANK_ID.
     * Nullable: rows created before the QR feature have no BIN.
     */
    @Column(name = "bank_bin", length = 20)
    private String bankBin;

    @Column(name = "account_name", length = 100)
    private String accountName;

    @Column(name = "qr_image", columnDefinition = "MEDIUMTEXT")
    private String qrImage;

    @Column(name = "default_message", length = 250)
    private String defaultMessage;

    /**
     * ACTIVE | DEACTIVE (DEACTIVE = removed by owner)
     */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "created_at", updatable = false, insertable = false)
    private Timestamp createdAt;

    @Column(name = "updated_at")
    private Timestamp updatedAt;
}
