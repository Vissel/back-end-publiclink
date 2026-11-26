package com.qrpublic.apartment.repository;

import com.qrpublic.apartment.entity.BadmintonCourtSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BadmintonCourtScheduleRepository extends JpaRepository<BadmintonCourtSchedule, Long> {
    List<BadmintonCourtSchedule> findByCourtCourtIdOrderByDayOfWeekAsc(Long courtId);

    Optional<BadmintonCourtSchedule> findByCourtCourtIdAndDayOfWeek(Long courtId, int dayOfWeek);
}
