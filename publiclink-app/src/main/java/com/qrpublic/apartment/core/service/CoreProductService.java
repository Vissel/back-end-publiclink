package com.qrpublic.apartment.core.service;

import com.qrpublic.apartment.core.model.PictureModel;
import com.qrpublic.apartment.core.model.ProductModel;
import com.qrpublic.apartment.entity.Picture;
import com.qrpublic.apartment.entity.Product;
import com.qrpublic.apartment.entity.ProductPictureMap;
import com.qrpublic.apartment.entity.Request;
import com.qrpublic.apartment.product.request.CreateProductRequest;
import com.qrpublic.apartment.repository.ProductRepository;
import com.qrpublic.apartment.repository.RequestRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CoreProductService {
    @Autowired
    ProductRepository productRepository;

    @Autowired
    RequestRepository requestRepository;

    public List<Product> getProductsByRequest(Request request) {
        return productRepository.findProductsByRequest(request);
    }

    /**
     * Get products with their pictures by request ID.
     * Uses LEFT JOIN FETCH to eagerly load pictures.
     */
    public List<Product> getProductsWithPicturesByRequestId(Long requestId) {
        return productRepository.findProductsWithPicturesByRequestId(requestId);
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

    /**
     * Create a product with images for a given request.
     * <p>
     * Banking System Best Practices:
     * 1. Retrieve the request entity with pessimistic locking
     * 2. Validate business rules
     * 3. Create product with proper transaction management
     * 4. Handle relationships correctly using cascading
     *
     * @param requestId The UUID of the request
     * @return The created Product entity
     */
    @Transactional
    public Product createProductProdImage(long requestId, ProductModel productModel) {
        // STEP 1: Retrieve the request entity
        log.info("Step 1: Retrieving request entity for request ID: {}", requestId);
        Optional<Request> requestOpt = requestRepository.findByIdForUpdate(requestId);
        if (requestOpt.isEmpty()) {
            log.error("Request not found with UUID: {}", requestId);
            throw new IllegalArgumentException("Request not found with UUID: " + requestId);
        }
        Request requestEntity = requestOpt.get();
        log.debug("Successfully retrieved request: {}", requestEntity.getReqUUID());

        // STEP 2: Create product with proper transaction management
        // @Transactional annotation ensures ACID properties
        log.info("Step 2: Creating product with transaction management");
        Product product = buildProductEntityForNew(productModel, requestEntity);

        // STEP 3: Handle relationships correctly using cascading
        // CascadeType.ALL + orphanRemoval=true ensures proper lifecycle management
        log.info("Step 3: Saving product with cascading relationships");
        Product savedProduct = productRepository.save(product);

        log.info("Successfully created product '{}' with {} image(s) for request: {}",
                savedProduct.getProductName(),
                savedProduct.getListPicProMap() != null ? savedProduct.getListPicProMap().size() : 0,
                requestId);

        return savedProduct;
    }

    /**
     * Validate the request entity is in a proper state for product creation
     */
    private void validateRequestState(Request requestEntity) {
        if (requestEntity.getReqUUID() == null) {
            throw new IllegalStateException("Request has invalid UUID");
        }
        // Add additional business validations as needed:
        // - Check if request is not expired
        // - Check if request is not deleted
        // - Check if request allows product creation
        // - Check user permissions
    }

    /**
     * Build the Product entity with all relationships
     */
    private Product buildProductEntityForNew(ProductModel productModel, Request requestEntity) {
        Product product = new Product();
        product.setProductName(productModel.getProductName());
        product.setAmount(productModel.getQuantity());
        product.setPrice(productModel.getPrice());
        product.setRequest(requestEntity);

        // Handle image relationships
        List<String> imageDataList = productModel.getPictureModels().stream().map(PictureModel::getData)
                .collect(Collectors.toList());
        if (imageDataList != null && !imageDataList.isEmpty()) {
            List<ProductPictureMap> picMapList = new ArrayList<>();
            for (String imageData : imageDataList) {
                if (imageData != null && !imageData.isBlank()) {
                    // Create Picture entity
                    Picture picture = new Picture();
                    picture.setTitle("product-image");
                    picture.setData(imageData);

                    // Create mapping entity (bidirectional relationship)
                    ProductPictureMap picMap = new ProductPictureMap(product, picture);
                    picMapList.add(picMap);
                }
            }
            // Set the relationship - cascade will handle persistence
            product.setListPicProMap(picMapList);
        }

        return product;
    }
}
