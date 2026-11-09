package com.qrpublic.apartment.repository;

import com.qrpublic.apartment.entity.Pricing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PricingRepository extends JpaRepository<Pricing, Long> {

    List<Pricing> findByRequest_ReqId(Long reqId);

    List<Pricing> findByRequest_ReqUUID(String reqUUID);

    List<Pricing> findByRequest_ReqIdIn(List<Long> reqIds);
}
