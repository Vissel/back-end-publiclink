package com.qrpublic.apartment.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "product_picture_map")
@Getter
@Setter
@NoArgsConstructor
public class ProductPictureMap {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private int map_id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id")
	private Product product;

	@OneToOne(fetch = FetchType.LAZY, cascade = { CascadeType.ALL }, orphanRemoval = true)
	@JoinColumn(name = "pic_id")
	private Picture picture;

	public ProductPictureMap(Product p, Picture pic) {
		this.product = p;
		this.picture = pic;
	}
}
