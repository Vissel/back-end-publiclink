package com.qrpublic.apartment.chat.repository;

import com.qrpublic.apartment.chat.entity.ZaloEventLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ZaloEventLogRepository extends JpaRepository<ZaloEventLog, Long> {

    boolean existsByEventId(String eventId);

    @Query("SELECT e FROM ZaloEventLog e WHERE e.processed = false ORDER BY e.id ASC")
    List<ZaloEventLog> findUnprocessed(Pageable pageable);

    @Modifying
    @Query("UPDATE ZaloEventLog e SET e.processed = true WHERE e.id = :id")
    void markProcessed(@Param("id") Long id);
}
