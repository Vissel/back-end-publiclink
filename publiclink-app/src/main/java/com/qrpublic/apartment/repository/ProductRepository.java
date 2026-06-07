package com.qrpublic.apartment.repository;

import com.qrpublic.apartment.entity.Product;
import com.qrpublic.apartment.entity.Request;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Lock(LockModeType.PESSIMISTIC_READ)
    List<Product> findProductsByRequest(Request request);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT p FROM Product p WHERE p.request.reqId IN :requestIds")
    List<Product> findProductsByRequestIds(@Param("requestIds") List<Long> requestIds);

    @Query("SELECT DISTINCT p FROM Product p " +
            "LEFT JOIN FETCH p.listPicProMap ppm " +
            "LEFT JOIN FETCH ppm.picture " +
            "WHERE p.request.reqId = :requestId")
    List<Product> findProductsWithPicturesByRequestId(@Param("requestId") Long requestId);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.listPicProMap WHERE p.productId = :productId")
    Optional<Product> findByIdWithPicMaps(@Param("productId") Long productId);
}
