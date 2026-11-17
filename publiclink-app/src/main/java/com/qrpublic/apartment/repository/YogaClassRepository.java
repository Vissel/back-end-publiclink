package com.qrpublic.apartment.repository;

import com.qrpublic.apartment.entity.YogaClass;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface YogaClassRepository extends JpaRepository<YogaClass, Long> {

        Optional<YogaClass> findByClassCode(String classCode);

        @Query("SELECT c FROM YogaClass c WHERE c.saleEnvironment.envId = :envId AND c.active = true"
                        + " ORDER BY c.dayOfWeek ASC, c.startTime ASC")
        List<YogaClass> findActiveByEnvId(@Param("envId") String envId);

        /**
         * Overlap check on the same env + weekday: two active classes may not
         * share any minute. excludeCode skips the class being updated.
         */
        @Query("SELECT c FROM YogaClass c WHERE c.saleEnvironment.envId = :envId "
                        + "AND c.dayOfWeek = :dayOfWeek AND c.active = true "
                        + "AND c.startTime < :endTime AND c.endTime > :startTime "
                        + "AND (:excludeCode IS NULL OR c.classCode <> :excludeCode)")
        List<YogaClass> findOverlapping(@Param("envId") String envId,
                        @Param("dayOfWeek") int dayOfWeek,
                        @Param("startTime") LocalTime startTime,
                        @Param("endTime") LocalTime endTime,
                        @Param("excludeCode") String excludeCode);

        /**
         * Serialize capacity checks per weekly occurrence: the concrete date
         * maps back to the ISO weekday of the class row.
         */
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT c FROM YogaClass c WHERE c.classCode = :classCode")
        Optional<YogaClass> findByClassCodeForUpdate(@Param("classCode") String classCode);
}
