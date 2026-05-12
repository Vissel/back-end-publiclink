package com.qrpublic.apartment.repository;

import com.qrpublic.apartment.entity.Request;
import com.qrpublic.apartment.entity.SaleEnvironment;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SaleEnvironmentRepository extends JpaRepository<SaleEnvironment, String> {
    Optional<SaleEnvironment> findByPublicLink(String publicLink);

    Optional<SaleEnvironment> findByRequest(Request request);

    @Query("SELECT se FROM SaleEnvironment se WHERE se.request.sellerName = :sellerName")
    Page<SaleEnvironment> findBySellerName(@Param("sellerName") String sellerName, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT se FROM SaleEnvironment se JOIN FETCH se.request r JOIN FETCH r.products")
    List<SaleEnvironment> findAllWithProducts();

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT se FROM SaleEnvironment se LEFT JOIN FETCH se.listOrder WHERE se.envId IN :ids")
    List<SaleEnvironment> findAllWithOrdersByIds(@Param("ids") List<String> ids);

    @Query("SELECT se FROM SaleEnvironment se LEFT JOIN FETCH se.listOrder WHERE se.envId = :envId")
    Optional<SaleEnvironment> findWithOrdersById(@Param("envId") String envId);

    @Query("SELECT se FROM SaleEnvironment se JOIN FETCH se.request r LEFT JOIN FETCH r.products WHERE se.envId = :envId")
    Optional<SaleEnvironment> findWithProductsById(@Param("envId") String envId);

}
