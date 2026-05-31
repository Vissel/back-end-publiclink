package com.qrpublic.apartment.core.service;

import com.qrpublic.apartment.entity.Picture;
import com.qrpublic.apartment.entity.Product;
import com.qrpublic.apartment.entity.ProductPictureMap;
import com.qrpublic.apartment.entity.Request;
import com.qrpublic.apartment.repository.ProductRepository;
import com.qrpublic.apartment.product.request.CreateProductRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
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

    /**
     * Persist a Product entity with its associated images.
     * Cascade rules handle saving ProductPictureMap and Picture entities.
     */
    public Product saveProduct(CreateProductRequest productRequest, Request requestEntity) {
        Product product = new Product();
        product.setProductName(productRequest.getName());
        product.setAmount(productRequest.getQuantity());
        product.setPrice(productRequest.getPrice());
        product.setRequest(requestEntity);

        List<String> imageDataList = productRequest.getImageDataList();
        if (imageDataList != null && !imageDataList.isEmpty()) {
            List<ProductPictureMap> picMapList = new ArrayList<>();
            for (String imageData : imageDataList) {
                if (imageData != null && !imageData.isBlank()) {
                    Picture picture = new Picture();
                    picture.setTitle("product-image");
                    picture.setData(imageData);
                    ProductPictureMap picMap = new ProductPictureMap(product, picture);
                    picMapList.add(picMap);
                }
            }
            product.setListPicProMap(picMapList);
        }

        productRepository.save(product);
        log.info("Saved product '{}' with {} image(s) for request: {}",
                product.getProductName(),
                imageDataList != null ? imageDataList.size() : 0,
                requestEntity.getReqUUID());

        return product;
    }

}
