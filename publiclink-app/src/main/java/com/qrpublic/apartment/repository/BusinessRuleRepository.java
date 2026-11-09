package com.qrpublic.apartment.repository;

import com.qrpublic.apartment.entity.BusinessRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.math.BigDecimal;

@Repository
public interface BusinessRuleRepository extends JpaRepository<BusinessRule, Long> {
    Optional<BusinessRule> findFirstByFeeAndDay(BigDecimal fee, int day);
}
