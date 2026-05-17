package com.qrpublic.apartment.repository;

import com.qrpublic.apartment.entity.Order;
import com.qrpublic.apartment.entity.SaleEnvironment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    @Lock(LockModeType.PESSIMISTIC_READ)
    List<Order> findOrdersBySaleEnvironment(SaleEnvironment saleEnvironment);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT o FROM Order o WHERE o.saleEnvironment.envId IN :envIds")
    List<Order> findOrdersByEnvironmentIds(@Param("envIds") List<String> envIds);
}
