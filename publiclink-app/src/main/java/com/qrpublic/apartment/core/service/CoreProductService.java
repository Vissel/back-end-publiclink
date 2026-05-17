package com.qrpublic.apartment.core.service;

import com.qrpublic.apartment.entity.Product;
import com.qrpublic.apartment.entity.Request;
import com.qrpublic.apartment.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CoreProductService {
    @Autowired
    ProductRepository productRepository;

    public List<Product> getProductsByRequest(Request request) {
        return productRepository.findProductsByRequest(request);
    }

    public Map<Long, List<Product>> getProductsMapByRequestIds(List<Long> requestIds) {
        return productRepository.findProductsByRequestIds(requestIds).stream()
                .collect(Collectors.groupingBy(p -> p.getRequest().getReqId()));
    }
}
