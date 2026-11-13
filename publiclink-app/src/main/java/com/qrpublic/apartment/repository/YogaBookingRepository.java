package com.qrpublic.apartment.repository;

import com.qrpublic.apartment.entity.YogaBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface YogaBookingRepository extends JpaRepository<YogaBooking, Long> {

        Optional<YogaBooking> findByBookingCode(String bookingCode);

        @Query("SELECT COUNT(b) FROM YogaBooking b WHERE b.yogaClass.classId = :classId"
                        + " AND b.bookingDate = :bookingDate AND b.status = 'BOOKED'")
        long countBookedSlots(@Param("classId") Long classId,
                        @Param("bookingDate") LocalDate bookingDate);

        /**
         * Bookings of the given classes inside a date window (calendar week
         * assembly and the below-calendar list).
         */
        @Query("SELECT b FROM YogaBooking b WHERE b.yogaClass.classId IN :classIds"
                        + " AND b.bookingDate BETWEEN :fromDate AND :toDate AND b.status = 'BOOKED'"
                        + " ORDER BY b.bookingDate ASC")
        List<YogaBooking> findByClassesAndDateRange(@Param("classIds") List<Long> classIds,
                        @Param("fromDate") LocalDate fromDate,
                        @Param("toDate") LocalDate toDate);

        /**
         * A plan buyer may consume at most one day per calendar date: any
         * still-open booking of the same mode on the same date blocks a new
         * PLAN booking (1 day -> 1 class).
         */
        @Query("SELECT COUNT(b) FROM YogaBooking b WHERE b.buyerUsername = :buyerUsername"
                        + " AND b.bookingDate = :bookingDate AND b.status = 'BOOKED'"
                        + " AND b.paymentMode = 'PLAN'")
        long countPlanBookingsOnDate(@Param("buyerUsername") String buyerUsername,
                        @Param("bookingDate") LocalDate bookingDate);

        /**
         * Duplicate guard for the same account buyer in one occurrence.
         */
        @Query("SELECT b FROM YogaBooking b WHERE b.yogaClass.classId = :classId"
                        + " AND b.bookingDate = :bookingDate AND b.buyerUsername = :buyerUsername"
                        + " AND b.status = 'BOOKED'")
        Optional<YogaBooking> findOpenByBuyerAndSlot(@Param("classId") Long classId,
                        @Param("bookingDate") LocalDate bookingDate,
                        @Param("buyerUsername") String buyerUsername);

        /**
         * Seller booking list for one environment inside a date window.
         */
        @Query("SELECT b FROM YogaBooking b JOIN b.yogaClass c JOIN c.saleEnvironment se"
                        + " WHERE se.envId = :envId AND b.bookingDate BETWEEN :fromDate AND :toDate"
                        + " ORDER BY b.bookingDate DESC, c.startTime ASC")
        List<YogaBooking> findByEnvAndDateRange(@Param("envId") String envId,
                        @Param("fromDate") LocalDate fromDate,
                        @Param("toDate") LocalDate toDate);

        /**
         * Booked counts grouped by (classCode, date) for one environment in a
         * window: one query feeds the whole calendar week grid.
         */
        @Query("SELECT c.classCode, b.bookingDate, COUNT(b) FROM YogaBooking b JOIN b.yogaClass c"
                        + " JOIN c.saleEnvironment se WHERE se.envId = :envId"
                        + " AND b.bookingDate BETWEEN :fromDate AND :toDate AND b.status = 'BOOKED'"
                        + " GROUP BY c.classCode, b.bookingDate")
        List<Object[]> countBookedByEnvRange(@Param("envId") String envId,
                        @Param("fromDate") LocalDate fromDate,
                        @Param("toDate") LocalDate toDate);

        /**
         * Open bookings of one account buyer in an environment window, for
         * highlighting "my slots" cells.
         */
        @Query("SELECT b FROM YogaBooking b JOIN b.yogaClass c JOIN c.saleEnvironment se"
                        + " WHERE se.envId = :envId AND b.buyerUsername = :buyerUsername"
                        + " AND b.bookingDate BETWEEN :fromDate AND :toDate AND b.status = 'BOOKED'")
        List<YogaBooking> findOpenByBuyerInRange(@Param("envId") String envId,
                        @Param("buyerUsername") String buyerUsername,
                        @Param("fromDate") LocalDate fromDate,
                        @Param("toDate") LocalDate toDate);

        /**
         * Bookings of one account buyer across an environment (buyer banner +
         * "my bookings" highlight in the calendar).
         */
        @Query("SELECT b FROM YogaBooking b JOIN b.yogaClass c JOIN c.saleEnvironment se"
                        + " WHERE se.envId = :envId AND b.buyerUsername = :buyerUsername"
                        + " AND b.status = 'BOOKED' ORDER BY b.bookingDate ASC")
        List<YogaBooking> findByBuyerInEnv(@Param("envId") String envId,
                        @Param("buyerUsername") String buyerUsername);
}
