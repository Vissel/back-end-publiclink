package com.qrpublic.apartment.service;

import com.qrpublic.apartment.entity.Pricing;
import com.qrpublic.apartment.entity.Request;
import com.qrpublic.apartment.model.PriceModel;
import com.qrpublic.apartment.repository.PricingRepository;
import com.qrpublic.apartment.repository.RequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class PriceService {
    @Autowired
    PricingRepository pricingRepository;

    @Autowired
    RequestRepository requestRepository;

    @Transactional
    public void createPricingRecord(PriceModel priceModel) {
        if (priceModel == null || priceModel.getRequestModel() == null) {
            return;
        }

        Optional<Request> requestOpt = requestRepository.findByReqUUID(priceModel.getRequestModel().getRequestUuid());
        if (requestOpt.isPresent()) {
            Pricing pricing = new Pricing();
            pricing.setRequest(requestOpt.get());
            pricing.setDurationHours(priceModel.getDurationHours());
            pricing.setAmount(priceModel.getAmount());
            pricing.setCurrency(priceModel.getCurrency() != null ? priceModel.getCurrency() : "VND");
            pricingRepository.save(pricing);
        }
    }
}
