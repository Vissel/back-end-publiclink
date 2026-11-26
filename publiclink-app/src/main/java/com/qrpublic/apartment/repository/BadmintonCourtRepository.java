package com.qrpublic.apartment.repository;

import com.qrpublic.apartment.entity.BadmintonCourt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BadmintonCourtRepository extends JpaRepository<BadmintonCourt, Long> {
    Optional<BadmintonCourt> findByCourtCode(String courtCode);

    List<BadmintonCourt> findBySaleEnvironmentEnvIdOrderByCourtCodeAsc(String envId);

    List<BadmintonCourt> findBySaleEnvironmentEnvIdAndActiveTrueOrderByCourtCodeAsc(String envId);

    long countBySaleEnvironmentEnvId(String envId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM BadmintonCourt c WHERE c.courtCode = :courtCode")
    Optional<BadmintonCourt> findByCourtCodeForUpdate(@Param("courtCode") String courtCode);

    /** Serializes court creation so the six-court cap holds under concurrency. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM BadmintonCourt c JOIN c.saleEnvironment se"
            + " WHERE se.envId = :envId ORDER BY c.courtId ASC")
    List<BadmintonCourt> findAllByEnvIdForUpdate(@Param("envId") String envId);
}
