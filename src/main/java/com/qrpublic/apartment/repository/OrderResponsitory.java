package com.qrpublic.apartment.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.qrpublic.apartment.entity.Order;

public interface OrderResponsitory extends JpaRepository<Order, Long> {
}
