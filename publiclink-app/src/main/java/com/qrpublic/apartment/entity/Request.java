package com.qrpublic.apartment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;
import java.util.List;

@Entity
@Table(name = "request")
@Getter
@Setter
@NoArgsConstructor
public class Request {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long reqId;

    private String sellerName;

    private String description;

    @Column(updatable = false, insertable = false)
    private Timestamp createdAt;

    @OneToOne(cascade = CascadeType.DETACH)
    @JoinColumn(name = "created_by")
    private User createdBy;

    private boolean authenticated;

    @OneToMany(mappedBy = "request", cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE})
    private List<Product> products;

    @Column(name = "req_uuid", unique = true, nullable = false)
    private String reqUUID;

    private String reqAuthLink;
}
