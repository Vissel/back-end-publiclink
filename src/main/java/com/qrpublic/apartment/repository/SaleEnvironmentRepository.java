package com.qrpublic.apartment.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.qrpublic.apartment.entity.Request;
import com.qrpublic.apartment.entity.SaleEnvironment;

public interface SaleEnvironmentRepository extends JpaRepository<SaleEnvironment, String> {
	Optional<SaleEnvironment> findByPublicLink(String publicLink);

	Optional<SaleEnvironment> findByRequest(Request request);
}
