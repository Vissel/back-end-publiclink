package com.qrpublic.apartment.repository;

import com.qrpublic.apartment.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
