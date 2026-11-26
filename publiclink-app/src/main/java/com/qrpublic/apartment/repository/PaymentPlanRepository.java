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

        /**
         * One purpose group of the catalog, ordered so a screen reading it top
         * down walks from the cheapest plan to the dearest. The admin catalog
         * keeps using {@link #findAllActive()} because an admin assigns plans to
         * both kinds of account.
         */
        @Query("SELECT p FROM PaymentPlan p WHERE p.planState = 'ACTIVE' AND p.planGroup = :planGroup "
                        + "ORDER BY p.fee ASC, p.planDay ASC, p.envCount ASC")
        List<PaymentPlan> findAllActiveByGroup(@Param("planGroup") String planGroup);
}
