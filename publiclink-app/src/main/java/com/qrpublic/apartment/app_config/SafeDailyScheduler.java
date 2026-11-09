package com.qrpublic.apartment.app_config;

import com.qrpublic.apartment.saleenv.SaleEnvironmentTimingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Safe hourly scheduler that expires sale environments past their willEndedAt.
 * Runs once on application startup and then every hour (Asia/Ho_Chi_Minh).
 */
@Slf4j
@Component
public class SafeDailyScheduler {

    private final AtomicBoolean appReady = new AtomicBoolean(false);

    @Autowired
    SaleEnvironmentTimingService saleEnvironmentTimingService;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        appReady.set(true);
        runHourlyJob();
    }

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Ho_Chi_Minh")
    public void runHourlyJob() {
        if (!appReady.get()) {
            log.warn("Skipping scheduled job — application not ready yet.");
            return;
        }

        log.info("Running hourly scheduler job at: {}", java.time.LocalDateTime.now());
        ResponseEntity<String> response = saleEnvironmentTimingService.closeExpiredEnvironments();
        log.info("Scheduled task result: {}", response.getBody());
    }
}
