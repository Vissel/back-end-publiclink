package com.qrpublic.apartment.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.qrpublic.apartment.entity.User;

public interface UserRepository extends JpaRepository<User, String> {
	/**
	 * name and link are unique constraint
	 * 
	 * @param name
	 * @param link
	 * @return
	 */
	Optional<User> findByNameAndLink(String name, String link);

	Optional<User> findByUserName(String username);
}
