package com.qrpublic.apartment.entity;

import com.qrpublic.apartment.constant.CommonConstant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;
import java.util.List;

@Entity
@Table(name = "sale_environment")
@Getter
@Setter
@NoArgsConstructor
public class SaleEnvironment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "env_id")
    private String envId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "req_id")
    private Request request;

    @Column(nullable = false)
    private String publicLink;

    private boolean state;

    @Column(updatable = false, insertable = false)
    private Timestamp createdAt;

    @Column(updatable = false, insertable = false)
    private Timestamp endedAt;

    @Column(name = "will_ended_at")
    private Timestamp willEndedAt;

    /**
     * When the sale window opens. Null on rows created before the window
     * feature, which means "started as soon as it was created".
     */
    @Column(name = "will_started_at")
    private Timestamp willStartedAt;

    /**
     * transfer_info.transfer_code of the account shown on the sale page so
     * buyers can pay. Null when the seller chose not to show one. Kept as a
     * plain code (no FK): a deactivated account simply stops being displayed.
     */
    @Column(name = "transfer_code", length = 36)
    private String transferCode;

    @OneToMany(mappedBy = "saleEnvironment", cascade = CascadeType.ALL)
    @OrderBy(value = "orderedAt DESC")
    private List<Order> listOrder;

    public SaleEnvironment(Request req) {
        this.request = req;
        this.publicLink = CommonConstant.EMPTY;
    }
}
