package com.qrpublic.apartment.repository;

import com.qrpublic.apartment.entity.PaymentPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentPlanRepository extends JpaRepository<PaymentPlan, Long> {

        Optional<PaymentPlan> findByPlanCode(String planCode);

        @Query("SELECT p FROM PaymentPlan p WHERE p.planState = 'ACTIVE' ORDER BY p.fee ASC, p.planDay ASC")
        List<PaymentPlan> findAllActive();

        @Query("SELECT p FROM PaymentPlan p " +
                        "WHERE p.planState = 'ACTIVE' AND p.fee = :fee AND p.planDay = :planDay AND p.envCount = :envCount")
        Optional<PaymentPlan> findActiveByFeeAndDayAndEnv(@Param("fee") java.math.BigDecimal fee,
                        @Param("planDay") int planDay,
                        @Param("envCount") int envCount);
}
