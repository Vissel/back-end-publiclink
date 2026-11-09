package com.qrpublic.apartment.core.service;

import com.qrpublic.apartment.entity.SaleEnvironment;
import com.qrpublic.apartment.repository.SaleEnvironmentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;

/**
 * Core service for sale environment state transitions.
 * Used by scheduler and other services to manage environment lifecycle.
 */
@Slf4j
@Service
public class CoreSaleEnvironmentService {

    @Autowired
    private SaleEnvironmentRepository saleEnvironmentRepository;

    /**
     * Expires a sale environment: sets state to false and endedAt to the given time.
     *
     * @param envId the environment ID
     * @param now   the current timestamp from DB timezone
     * @return number of rows updated (1 if successful, 0 if not found)
     */
    @Transactional
    public int expireEnvironment(String envId, Timestamp now) {
        log.info("Expiring environment envId={}, endedAt={}", envId, now);
        int updated = saleEnvironmentRepository.expireEnvironment(envId, now);
        if (updated > 0) {
            log.info("Environment expired successfully: envId={}", envId);
        } else {
            log.warn("No environment found to expire: envId={}", envId);
        }
        return updated;
    }

    /**
     * Finds all active environments whose willEndedAt is before the given timestamp.
     *
     * @param now the current timestamp from DB timezone
     * @return list of expired but still active environments
     */
    @Transactional(readOnly = true)
    public List<SaleEnvironment> findExpiredActiveEnvironments(Timestamp now) {
        return saleEnvironmentRepository.findExpiredEnvironments(now);
    }
}
