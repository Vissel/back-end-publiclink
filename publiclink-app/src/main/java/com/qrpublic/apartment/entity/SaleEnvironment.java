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

    @OneToOne(optional = false, fetch = FetchType.LAZY, cascade = {CascadeType.REMOVE})
    @JoinColumn(name = "req_id")
    private Request request;

    @Column(nullable = false)
    private String publicLink;

    private boolean state = true;

    @Column(updatable = false, insertable = false)
    private Timestamp createdAt;

    @Column(updatable = false, insertable = false)
    private Timestamp endedAt;

    @OneToMany(mappedBy = "saleEnvironment", cascade = {CascadeType.REMOVE})
    @OrderBy(value = "DESC")
    private List<Order> listOrder;

    public SaleEnvironment(Request req) {
        this.request = req;
        this.publicLink = CommonConstant.EMPTY;
    }
}
