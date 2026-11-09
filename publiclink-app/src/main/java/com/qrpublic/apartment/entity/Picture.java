package com.qrpublic.apartment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "picture")
@Getter
@Setter
@NoArgsConstructor
public class Picture {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long picId;

	private String link;

	private String title;

	@Column(columnDefinition = "LONGTEXT")
	private String data;

	public Picture(String l, String t) {
		this.link = l;
		this.title = t;
	}
}
