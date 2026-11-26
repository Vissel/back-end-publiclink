package com.qrpublic.apartment.saleenv.impl;

import com.qrpublic.apartment.core.service.CoreRequestService;
import com.qrpublic.apartment.core.service.CoreTimezoneService;
import com.qrpublic.apartment.core.service.CoreUserService;
import com.qrpublic.apartment.domain.transferinfo.EnvironmentTransferDomain;
import com.qrpublic.apartment.entity.Picture;
import com.qrpublic.apartment.entity.Product;
import com.qrpublic.apartment.entity.ProductPictureMap;
import com.qrpublic.apartment.entity.Request;
import com.qrpublic.apartment.integration.SecurityCheckClient;
import com.qrpublic.apartment.repository.ProductRepository;
import com.qrpublic.apartment.repository.SaleEnvironmentRepository;
import com.qrpublic.apartment.requestmodel.ProductPictureRequest;
import com.qrpublic.apartment.requestmodel.UpdateProductRequest;
import com.qrpublic.apartment.service.LinkService;
import com.qrpublic.apartment.template.service.PublicLinkServiceTemplate;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ActiveProfiles("test")
@DataJpaTest(properties = {
        "spring.liquibase.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:product-update;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-test.sql",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.globally_quoted_identifiers=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(SaleSpaceServiceImpl.class)
class SaleSpaceServiceImplTest {

    @Autowired
    private SaleSpaceServiceImpl saleSpaceService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private PublicLinkServiceTemplate publicLinkServiceTemplate;
    @MockitoBean
    private LinkService linkService;
    @MockitoBean
    private CoreRequestService coreRequestService;
    @MockitoBean
    private CoreUserService coreUserService;
    @MockitoBean
    private SaleEnvironmentRepository saleEnvironmentRepository;
    @MockitoBean
    private CoreTimezoneService coreTimezoneService;
    @MockitoBean
    private SecurityCheckClient securityCheckClient;
    @MockitoBean
    private EnvironmentTransferDomain environmentTransferDomain;

    private Product product;
    private Picture oldPicture;

    @BeforeEach
    void setUp() {
        when(linkService.validateLink("valid-token")).thenReturn(true);

        Request request = new Request();
        request.setReqUUID(UUID.randomUUID().toString());
        request.setSellerName("seller");
        entityManager.persist(request);

        product = new Product();
        product.setProductName("Old name");
        product.setAmount(5);
        product.setPrice(100.0);
        product.setUnit("VND");
        product.setRequest(request);
        product.setListPicProMap(new ArrayList<>());

        oldPicture = new Picture();
        oldPicture.setTitle("old-image");
        oldPicture.setData("data:image/png;base64,old");
        product.getListPicProMap().add(new ProductPictureMap(product, oldPicture));
        entityManager.persist(product);
        entityManager.flush();
    }

    @Test
    void updateProductCommitsWhenPicturesAreUnchanged() {
        UpdateProductRequest request = updateRequest(null);

        assertThat(saleSpaceService.updateProduct(request)).isTrue();
        entityManager.flush();
        entityManager.clear();

        Product updated = productRepository.findByIdWithPicMaps(product.getProductId()).orElseThrow();
        assertThat(updated.getProductName()).isEqualTo("Updated name");
        assertThat(updated.getAmount()).isEqualTo(8);
        assertThat(updated.getPrice()).isEqualTo(250.0);
        assertThat(updated.getUnit()).isEqualTo("cái");
        assertThat(updated.getListPicProMap()).hasSize(1);
        assertThat(updated.getListPicProMap().getFirst().getPicture().getPicId())
                .isEqualTo(oldPicture.getPicId());
    }

    @Test
    void updateProductReplacesPicturesWithoutDereferencingManagedCollection() {
        UpdateProductRequest request = updateRequest(List.of(
                new ProductPictureRequest("product-image", "data:image/png;base64,new")));

        assertThat(saleSpaceService.updateProduct(request)).isTrue();
        entityManager.flush();
        entityManager.clear();

        Product updated = productRepository.findByIdWithPicMaps(product.getProductId()).orElseThrow();
        assertThat(updated.getListPicProMap()).hasSize(1);
        Picture replacement = updated.getListPicProMap().getFirst().getPicture();
        assertThat(replacement.getPicId()).isNotEqualTo(oldPicture.getPicId());
        assertThat(replacement.getData()).isEqualTo("data:image/png;base64,new");
        assertThat(entityManager.find(Picture.class, oldPicture.getPicId())).isNull();
    }

    @Test
    void updateProductCanRemoveAllPictures() {
        UpdateProductRequest request = updateRequest(List.of());

        assertThat(saleSpaceService.updateProduct(request)).isTrue();
        entityManager.flush();
        entityManager.clear();

        Product updated = productRepository.findByIdWithPicMaps(product.getProductId()).orElseThrow();
        assertThat(updated.getListPicProMap()).isEmpty();
        assertThat(entityManager.find(Picture.class, oldPicture.getPicId())).isNull();
    }

    private UpdateProductRequest updateRequest(List<ProductPictureRequest> pictures) {
        UpdateProductRequest request = new UpdateProductRequest();
        request.setToken("valid-token");
        request.setProductId(product.getProductId());
        request.setProductName("Updated name");
        request.setAmount(8);
        request.setPrice(250.0);
        request.setUnit("cái");
        request.setListPicProMap(pictures);
        return request;
    }
}
