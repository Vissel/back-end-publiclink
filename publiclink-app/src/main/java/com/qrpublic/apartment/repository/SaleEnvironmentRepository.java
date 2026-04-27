package com.qrpublic.apartment.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.qrpublic.apartment.entity.Request;
import com.qrpublic.apartment.entity.SaleEnvironment;

public interface SaleEnvironmentRepository extends JpaRepository<SaleEnvironment, String> {
	Optional<SaleEnvironment> findByPublicLink(String publicLink);

	Optional<SaleEnvironment> findByRequest(Request request);

	@Query("SELECT se FROM SaleEnvironment se WHERE se.request.sellerId.userName = :sellerName")
	Page<SaleEnvironment> findBySellerName(@Param("sellerName") String sellerName, Pageable pageable);
}
