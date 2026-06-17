package com.qrpublic.apartment.repository;

import com.qrpublic.apartment.entity.Request;
import com.qrpublic.apartment.entity.SaleEnvironment;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

public interface SaleEnvironmentRepository extends JpaRepository<SaleEnvironment, String> {

        Optional<SaleEnvironment> findByPublicLink(String publicLink);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT se FROM SaleEnvironment se LEFT JOIN FETCH se.request r WHERE r.reqUUID = :reqUUID")
        Optional<SaleEnvironment> findByPublicLinkWithWriteLock(@Param("reqUUID") String reqUUID);

        Optional<SaleEnvironment> findFirstByRequestOrderByCreatedAtDesc(Request request);

        @Query("SELECT se FROM SaleEnvironment se LEFT JOIN FETCH se.request r WHERE r.sellerName = :sellerName")
        Page<SaleEnvironment> findBySellerName(@Param("sellerName") String sellerName, Pageable pageable);

        @Query("SELECT se FROM SaleEnvironment se LEFT JOIN FETCH se.request r WHERE r.sellerName = :sellerName ORDER BY se.createdAt DESC")
        List<SaleEnvironment> findAllBySellerName(@Param("sellerName") String sellerName);

        @Query("SELECT COUNT(se) FROM SaleEnvironment se LEFT JOIN se.request r WHERE r.sellerName = :sellerName")
        long countBySellerName(@Param("sellerName") String sellerName);

        @Transactional(readOnly = true)
        @Lock(LockModeType.PESSIMISTIC_READ)
        @Query("SELECT se FROM SaleEnvironment se JOIN FETCH se.request r JOIN FETCH r.products WHERE se.envId IN :ids")
        List<SaleEnvironment> findAllWithProductsByIds(@Param("ids") List<String> ids);

        @Transactional(readOnly = true)
        @Lock(LockModeType.PESSIMISTIC_READ)
        @Query("SELECT se FROM SaleEnvironment se JOIN FETCH se.request r JOIN FETCH r.products")
        List<SaleEnvironment> findAllWithProducts();

        @Transactional(readOnly = true)
        @Lock(LockModeType.PESSIMISTIC_READ)
        @Query("SELECT se FROM SaleEnvironment se ORDER BY se.createdAt DESC")
        List<SaleEnvironment> findWithPageable(Pageable pageable);

        @Transactional(readOnly = true)
        @Lock(LockModeType.PESSIMISTIC_READ)
        @Query("SELECT se FROM SaleEnvironment se LEFT JOIN FETCH se.listOrder WHERE se.envId IN :ids")
        List<SaleEnvironment> findAllWithOrdersByIds(@Param("ids") List<String> ids);

        @Query("SELECT se FROM SaleEnvironment se LEFT JOIN FETCH se.listOrder WHERE se.envId = :envId")
        Optional<SaleEnvironment> findWithOrdersById(@Param("envId") String envId);

        @Query("SELECT se FROM SaleEnvironment se JOIN FETCH se.request r LEFT JOIN FETCH r.products WHERE se.envId = :envId")
        Optional<SaleEnvironment> findWithProductsById(@Param("envId") String envId);

        @Query("SELECT DISTINCT se FROM SaleEnvironment se " +
                        "JOIN FETCH se.request r " +
                        "WHERE se.envId = :envId")
        Optional<SaleEnvironment> findWithRequestById(@Param("envId") String envId);

        @Query("SELECT se FROM SaleEnvironment se LEFT JOIN FETCH se.request r LEFT JOIN FETCH r.products WHERE r.reqUUID = :reqUuid")
        Optional<SaleEnvironment> findByRequestReqUuid(@Param("reqUuid") String reqUuid);

        @Query("SELECT se FROM SaleEnvironment se " +
                        "LEFT JOIN se.request r " +
                        "LEFT JOIN r.createdBy cb " +
                        "WHERE (:createdAt IS NULL OR DATE(se.createdAt) = DATE(:createdAt)) " +
                        "AND (:createdBy IS NULL OR cb.userName LIKE CONCAT('%', :createdBy, '%')) " +
                        "AND (:sellerName IS NULL OR r.sellerName LIKE CONCAT('%',:sellerName,'%')) " +
                        "AND (:requestUuid IS NULL OR r.reqUUID LIKE CONCAT('%', :requestUuid, '%'))")
        Page<SaleEnvironment> findByFilters(
                        @Param("createdAt") String createdAt,
                        @Param("createdBy") String createdBy,
                        @Param("sellerName") String sellerName,
                        @Param("requestUuid") String requestUuid,
                        Pageable pageable);

        @Query("SELECT se FROM SaleEnvironment se WHERE se.state = true AND se.willEndedAt < :now")
        List<SaleEnvironment> findExpiredEnvironments(@Param("now") Timestamp now);

        @Modifying
        @Query("UPDATE SaleEnvironment se SET se.state = false, se.endedAt = :now WHERE se.envId = :envId")
        int expireEnvironment(@Param("envId") String envId, @Param("now") Timestamp now);

        @Query("SELECT o.saleEnvironment.envId, COUNT(o) FROM Order o WHERE o.saleEnvironment.envId IN :ids GROUP BY o.saleEnvironment.envId")
        List<Object[]> countOrdersByEnvIds(@Param("ids") List<String> ids);

        @Query("SELECT se.envId, COALESCE(SUM(p.total_amount), 0) FROM SaleEnvironment se JOIN se.request r JOIN r.products p WHERE se.envId IN :ids GROUP BY se.envId")
        List<Object[]> sumProductQuantityByEnvIds(@Param("ids") List<String> ids);

        @Query("SELECT DISTINCT se FROM SaleEnvironment se " +
                        "LEFT JOIN FETCH se.request r " +
                        "LEFT JOIN FETCH r.createdBy " +
                        "LEFT JOIN FETCH r.products")
        List<SaleEnvironment> findAllWithRequestAndProducts();

        @Query("SELECT r.sellerName, COUNT(se) FROM SaleEnvironment se " +
                        "JOIN se.request r WHERE r.sellerName IN :usernames " +
                        "GROUP BY r.sellerName")
        List<Object[]> countEnvironmentsBySellerUsernames(@Param("usernames") List<String> usernames);

        @Query("SELECT DISTINCT se FROM SaleEnvironment se " +
                        "LEFT JOIN FETCH se.request r " +
                        "LEFT JOIN FETCH r.products " +
                        "WHERE r.sellerName = :sellerUsername")
        List<SaleEnvironment> findBySellerUsername(@Param("sellerUsername") String sellerUsername);

}
