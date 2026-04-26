package com.qrpublic.apartment.repository;

import com.qrpublic.apartment.entity.Request;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface RequestRepository extends JpaRepository<Request, Long> {

    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<Request> findByUUID(String uuid);
}
