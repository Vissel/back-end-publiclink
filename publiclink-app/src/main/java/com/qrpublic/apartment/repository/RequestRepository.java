package com.qrpublic.apartment.repository;

import com.qrpublic.apartment.entity.Request;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface RequestRepository extends JpaRepository<Request, Long> {

    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<Request> findByReqUUID(String uuid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Request r where r.id = :id")
    Optional<Request> findByIdForUpdate(Long id);
}
