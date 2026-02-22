package com.qrpublic.apartment.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.qrpublic.apartment.entity.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {

}
