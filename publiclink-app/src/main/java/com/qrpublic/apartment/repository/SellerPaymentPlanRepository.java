package com.qrpublic.apartment.repository;

import com.qrpublic.apartment.entity.SellerPaymentPlan;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SellerPaymentPlanRepository extends JpaRepository<SellerPaymentPlan, Long> {

        Optional<SellerPaymentPlan> findBySellerPlanCode(String sellerPlanCode);

        @Query("SELECT p FROM SellerPaymentPlan p WHERE p.saleEnvironment.envId = :envId"
                        + " ORDER BY p.createdAt DESC")
        List<SellerPaymentPlan> findByEnvId(@Param("envId") String envId);

        @Query("SELECT p FROM SellerPaymentPlan p WHERE p.saleEnvironment.envId = :envId"
                        + " AND p.buyerUsername = :buyerUsername")
        Optional<SellerPaymentPlan> findByEnvIdAndBuyerUsername(@Param("envId") String envId,
                        @Param("buyerUsername") String buyerUsername);

        /**
         * ACTIVE plan of one buyer in one environment: the day source for a
         * PLAN booking. Locked for update so concurrent bookings cannot
         * oversell the remaining days.
         */
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT p FROM SellerPaymentPlan p WHERE p.sellerPlanCode = :sellerPlanCode")
        Optional<SellerPaymentPlan> findBySellerPlanCodeForUpdate(@Param("sellerPlanCode") String sellerPlanCode);

        @Query("SELECT p FROM SellerPaymentPlan p WHERE p.buyerUsername = :buyerUsername"
                        + " AND p.state = 'ACTIVE' ORDER BY p.createdAt DESC")
        List<SellerPaymentPlan> findActiveByBuyerUsername(@Param("buyerUsername") String buyerUsername);
}
