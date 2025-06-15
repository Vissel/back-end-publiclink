package com.qrpublic.apartment.entity;

import java.sql.Timestamp;

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

@Entity
@Table(name = "`order`", schema = "publiclink-db")
@Getter
@Setter
@NoArgsConstructor
public class Order {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long orderId;

	private String buyerName;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "env_id", nullable = false)
	private SaleEnvironment saleEnvironment;

	@Column(updatable = false, insertable = false)
	private Timestamp orderedAt;

	private boolean delivered;

	private boolean getMoney;

	private String sellerNote;
}
