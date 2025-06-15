package com.qrpublic.apartment.entity;

import java.sql.Timestamp;
import java.util.List;

import com.qrpublic.apartment.constant.CommonConstant;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

	@OneToOne(optional = false, fetch = FetchType.LAZY, cascade = { CascadeType.REMOVE })
	@JoinColumn(name = "req_id")
	private Request request;

	@Column(nullable = false)
	private String publicLink;

	private boolean state = true;

	@Column(updatable = false, insertable = false)
	private Timestamp createdAt;

	@Column(updatable = false, insertable = false)
	private Timestamp endedAt;

	@OneToMany(mappedBy = "saleEnvironment", cascade = { CascadeType.REMOVE })
	private List<Order> listOrder;

	public SaleEnvironment(Request req) {
		this.request = req;
		this.publicLink = CommonConstant.EMPTY;
	}
}
