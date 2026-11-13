package com.qrpublic.apartment.entity;

import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "product")
@Getter
@Setter
@NoArgsConstructor
public class Product {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long productId;

	private String productName;

	private int amount;

	private String unit;

	private Double price;

	private int total_amount;

	/**
	 * STANDARD (plain order link) or YOGA_BOOKING (calendar link).
	 * Column added by changeset 025; defaults keep legacy rows STANDARD.
	 */
	@Column(name = "product_type")
	private String productType = "STANDARD";

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "req_id")
	private Request request;

	@OneToMany(mappedBy = "product", cascade = { CascadeType.ALL }, orphanRemoval = true)
	List<ProductPictureMap> listPicProMap;
}
