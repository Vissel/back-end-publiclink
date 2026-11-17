package com.qrpublic.apartment.repository;

import com.qrpublic.apartment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

        Optional<Payment> findByPaymentCode(String paymentCode);

        @Query("SELECT pay FROM Payment pay JOIN pay.planUser ppu JOIN ppu.user u " +
                        "WHERE u.userName = :username ORDER BY pay.createdAt DESC")
        List<Payment> findByUserName(@Param("username") String username);

        @Query("SELECT pay FROM Payment pay JOIN pay.planUser ppu " +
                        "WHERE ppu.planUserCode IN :planUserCodes ORDER BY pay.createdAt DESC")
        List<Payment> findByPlanUserCodes(@Param("planUserCodes") List<String> planUserCodes);
}
