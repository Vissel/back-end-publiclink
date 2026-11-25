package com.qrpublic.apartment.repository;

import com.qrpublic.apartment.entity.BadmintonBooking;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BadmintonBookingRepository extends JpaRepository<BadmintonBooking, Long> {
    Optional<BadmintonBooking> findByBookingCode(String bookingCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BadmintonBooking b WHERE b.bookingCode = :bookingCode")
    Optional<BadmintonBooking> findByBookingCodeForUpdate(@Param("bookingCode") String bookingCode);

    /**
     * Called while holding the court write lock. Overlap (rather than exact
     * start-time equality) also protects history if a weekly schedule changes.
     */
    @Query("SELECT b FROM BadmintonBooking b WHERE b.court.courtId = :courtId"
            + " AND b.bookingDate = :bookingDate"
            + " AND b.status NOT IN ('CANCELLED', 'REJECTED')"
            + " AND b.startTime < :endTime AND b.endTime > :startTime")
    List<BadmintonBooking> findConflicting(@Param("courtId") Long courtId,
            @Param("bookingDate") LocalDate bookingDate,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime);

    @Query("SELECT b FROM BadmintonBooking b JOIN b.court c JOIN c.saleEnvironment se"
            + " WHERE se.envId = :envId AND b.bookingDate = :bookingDate"
            + " AND b.status NOT IN ('CANCELLED', 'REJECTED')"
            + " ORDER BY c.courtCode ASC, b.startTime ASC")
    List<BadmintonBooking> findBlockingByEnvAndDate(@Param("envId") String envId,
            @Param("bookingDate") LocalDate bookingDate);

    @Query("SELECT b FROM BadmintonBooking b JOIN b.court c JOIN c.saleEnvironment se"
            + " WHERE se.envId = :envId"
            + " ORDER BY b.bookingDate DESC, b.startTime ASC, c.courtCode ASC")
    List<BadmintonBooking> findByEnvId(@Param("envId") String envId);

    @Query("SELECT b FROM BadmintonBooking b JOIN b.court c JOIN c.saleEnvironment se"
            + " WHERE se.envId = :envId AND b.bookingDate = :bookingDate"
            + " ORDER BY b.startTime ASC, c.courtCode ASC")
    List<BadmintonBooking> findByEnvIdAndDate(@Param("envId") String envId,
            @Param("bookingDate") LocalDate bookingDate);

    @Query("SELECT b FROM BadmintonBooking b JOIN b.court c JOIN c.saleEnvironment se"
            + " WHERE se.envId = :envId AND b.buyerUsername = :buyerUsername"
            + " ORDER BY b.bookingDate DESC, b.startTime ASC, c.courtCode ASC")
    List<BadmintonBooking> findByEnvIdAndBuyerUsername(@Param("envId") String envId,
            @Param("buyerUsername") String buyerUsername);

    boolean existsByScheduleScheduleId(Long scheduleId);
}
