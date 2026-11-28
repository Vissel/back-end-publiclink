package com.qrpublic.apartment.repository;

import com.qrpublic.apartment.entity.PaymentPlanUser;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentPlanUserRepository extends JpaRepository<PaymentPlanUser, Long> {

        Optional<PaymentPlanUser> findByPlanUserCode(String planUserCode);

        List<PaymentPlanUser> findByPlanUserCodeIn(List<String> planUserCodes);

        /**
         * Search/filter/pagination for the admin setup tables. state is 'ALL'
         * handled by passing null. username matches the mapped user name too.
         */
        @Query("SELECT ppu FROM PaymentPlanUser ppu JOIN ppu.user u JOIN ppu.plan p " +
                        "WHERE (:state IS NULL OR ppu.planState = :state) " +
                        "AND (:username IS NULL OR u.userName LIKE CONCAT('%', :username, '%') " +
                        "     OR u.name LIKE CONCAT('%', :username, '%'))")
        Page<PaymentPlanUser> findPlans(@Param("state") String state,
                        @Param("username") String username,
                        Pageable pageable);

        @Query("SELECT COUNT(ppu) FROM PaymentPlanUser ppu JOIN ppu.user u JOIN ppu.plan p " +
                        "WHERE (:state IS NULL OR ppu.planState = :state) " +
                        "AND (:username IS NULL OR u.userName LIKE CONCAT('%', :username, '%') " +
                        "     OR u.name LIKE CONCAT('%', :username, '%'))")
        long countPlans(@Param("state") String state,
                        @Param("username") String username);

        /**
         * Assignments that may still produce an environment: ACTIVE, inside the
         * paid window (activeDate <= now < endedDate) and with quota left.
         * The window is authoritative: planState is never flipped automatically
         * when a plan expires, so without these predicates an ended plan would
         * keep selling links forever.
         */
        @Query("SELECT ppu FROM PaymentPlanUser ppu JOIN ppu.user u " +
                        "WHERE u.userName = :username AND ppu.planState = 'ACTIVE' " +
                        "AND ppu.activeDate IS NOT NULL AND ppu.activeDate <= :now " +
                        "AND ppu.endedDate IS NOT NULL AND ppu.endedDate > :now " +
                        "AND ppu.envQuota > ppu.usedCount ORDER BY ppu.activeDate ASC")
        List<PaymentPlanUser> findUsableActiveByUserName(@Param("username") String username,
                        @Param("now") java.sql.Timestamp now);

        /**
         * Usable slots of one purpose group only. A booking link may not be paid
         * for by a product plan and the other way round, so the generate flow
         * asks for the group its product type belongs to.
         */
        @Query("SELECT ppu FROM PaymentPlanUser ppu JOIN ppu.user u JOIN ppu.plan p " +
                        "WHERE u.userName = :username AND ppu.planState = 'ACTIVE' " +
                        "AND ppu.activeDate IS NOT NULL AND ppu.activeDate <= :now " +
                        "AND ppu.endedDate IS NOT NULL AND ppu.endedDate > :now " +
                        "AND ppu.envQuota > ppu.usedCount " +
                        "AND p.planGroup = :planGroup ORDER BY ppu.activeDate ASC")
        List<PaymentPlanUser> findUsableActiveByUserNameAndGroup(@Param("username") String username,
                        @Param("now") java.sql.Timestamp now,
                        @Param("planGroup") String planGroup);

        @Query("SELECT ppu FROM PaymentPlanUser ppu JOIN ppu.user u " +
                        "WHERE u.userName = :username AND ppu.planState = 'ACTIVE' ORDER BY ppu.createdAt DESC")
        List<PaymentPlanUser> findActiveByUserName(@Param("username") String username);

        /**
         * Same window rules as {@link #findActiveByUserName}, restricted to one
         * purpose group. Needed to tell "no plan at all" apart from "a plan, but
         * for the other kind of link", which are different messages and demand
         * different fixes.
         */
        @Query("SELECT ppu FROM PaymentPlanUser ppu JOIN ppu.user u JOIN ppu.plan p " +
                        "WHERE u.userName = :username AND ppu.planState = 'ACTIVE' " +
                        "AND p.planGroup = :planGroup ORDER BY ppu.createdAt DESC")
        List<PaymentPlanUser> findActiveByUserNameAndGroup(@Param("username") String username,
                        @Param("planGroup") String planGroup);

        /**
         * Every assignment of one seller, newest first. The self-service screen
         * reads this to tell "nothing bought yet" from "order waiting for pay".
         */
        @Query("SELECT ppu FROM PaymentPlanUser ppu JOIN ppu.user u " +
                        "WHERE u.userName = :username ORDER BY ppu.createdAt DESC")
        List<PaymentPlanUser> findAllByUserName(@Param("username") String username);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT ppu FROM PaymentPlanUser ppu JOIN ppu.user u " +
                        "WHERE u.userName = :username AND ppu.planState = 'ACTIVE' " +
                        "AND ppu.activeDate IS NOT NULL AND ppu.activeDate <= :now " +
                        "AND ppu.endedDate IS NOT NULL AND ppu.endedDate > :now " +
                        "AND ppu.envQuota > ppu.usedCount ORDER BY ppu.activeDate ASC")
        List<PaymentPlanUser> findUsableActiveByUserNameForUpdate(@Param("username") String username,
                        @Param("now") java.sql.Timestamp now);

        /**
         * Locked variant of {@link #findUsableActiveByUserNameAndGroup}, so a
         * generation can only ever spend a slot of the right purpose group.
         */
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT ppu FROM PaymentPlanUser ppu JOIN ppu.user u JOIN ppu.plan p " +
                        "WHERE u.userName = :username AND ppu.planState = 'ACTIVE' " +
                        "AND ppu.activeDate IS NOT NULL AND ppu.activeDate <= :now " +
                        "AND ppu.endedDate IS NOT NULL AND ppu.endedDate > :now " +
                        "AND ppu.envQuota > ppu.usedCount " +
                        "AND p.planGroup = :planGroup ORDER BY ppu.activeDate ASC")
        List<PaymentPlanUser> findUsableActiveByUserNameAndGroupForUpdate(@Param("username") String username,
                        @Param("now") java.sql.Timestamp now,
                        @Param("planGroup") String planGroup);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT ppu FROM PaymentPlanUser ppu WHERE ppu.planUserCode IN :codes")
        List<PaymentPlanUser> findByPlanUserCodeInForUpdate(@Param("codes") List<String> planUserCodes);

        /**
         * How many times an account has been granted a free (0 fee) plan. There is
         * deliberately no state filter: the free plan is a one-time offer, so a
         * grant counts even if an admin later rejected or deactivated it. Only real
         * plan rows are counted, never extension or conversion order rows, which
         * point at the same free plan without granting it again.
         */
        @Query("SELECT COUNT(ppu) FROM PaymentPlanUser ppu JOIN ppu.user u JOIN ppu.plan p " +
                        "WHERE u.userName = :username AND p.fee = 0 " +
                        "AND ppu.extendDays IS NULL AND ppu.convertsPlanUserCode IS NULL")
        long countFreePlanGrants(@Param("username") String username);
}
