package com.qrpublic.apartment.saleenv;

import com.qrpublic.apartment.core.service.CoreSaleEnvironmentService;
import com.qrpublic.apartment.core.service.CoreTimezoneService;
import com.qrpublic.apartment.entity.SaleEnvironment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;

/**
 * Scheduled service that checks for expired sale environments and closes them.
 * Called by SafeDailyScheduler on a daily basis (and on application startup).
 */
@Slf4j
@Service
public class SaleEnvironmentTimingService {

    @Autowired
    private CoreTimezoneService coreTimezoneService;

    @Autowired
    private CoreSaleEnvironmentService coreSaleEnvironmentService;

    /**
     * Finds all active sale environments whose willEndedAt is before DB now,
     * then sets their endedAt to now and state to false.
     *
     * @return ResponseEntity summarising how many environments were expired
     */
    public ResponseEntity<String> closeExpiredEnvironments() {
        Timestamp now = new Timestamp(coreTimezoneService.getNow().getTime());
        log.info("=== Scheduled: checking expired environments at {} ===", now);

        List<SaleEnvironment> expiredList = coreSaleEnvironmentService.findExpiredActiveEnvironments(now);

        if (expiredList.isEmpty()) {
            log.info("No expired environments found.");
            return ResponseEntity.ok("No expired environments.");
        }

        log.info("Found {} expired environment(s) to close.", expiredList.size());

        int successCount = 0;
        for (SaleEnvironment env : expiredList) {
            try {
                int updated = coreSaleEnvironmentService.expireEnvironment(env.getEnvId(), now);
                if (updated > 0) {
                    successCount++;
                }
            } catch (Exception e) {
                log.error("Failed to expire environment envId={}: {}", env.getEnvId(), e.getMessage(), e);
            }
        }

        String message = String.format("Expired %d/%d environment(s).", successCount, expiredList.size());
        log.info("=== Scheduled: {} ===", message);
        return ResponseEntity.ok(message);
    }
}
