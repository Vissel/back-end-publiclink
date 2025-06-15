package com.qrpublic.apartment.entity;

import java.sql.Timestamp;

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
@Table(name = "user")
@Getter
@Setter
@NoArgsConstructor

public class User {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private String userId;

	private String userName;
	private String tempPassword;
	private String name;
	private String link;
	private String type;

	@Column(updatable = false, insertable = false)
	private Timestamp createdAt;

	public User(String username, String pass, String name, String link, String type) {
		this.userName = username;
		this.tempPassword = pass;
		this.name = name;
		this.link = link;
		this.type = type;
	}
}
