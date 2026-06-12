package com.qrpublic.apartment.databackup.scheduler;
import com.qrpublic.apartment.databackup.entity.TriggerEntity.*;
import com.qrpublic.apartment.databackup.service.BackupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.List;

@Component @RequiredArgsConstructor @Slf4j
public class BackupScheduler {
    private final BackupService backupService;
    private static final List<String> ALL = List.of("publiclink-db", "user_schema");

    @Scheduled(cron = "${backup.schedule.daily-cron}")
    public void dailyBackup() {
        log.info("Daily backup triggered");
        BackupScope scope = LocalDate.now().getDayOfMonth() == 1 ? BackupScope.FULL : BackupScope.INCREMENTAL;
        backupService.executeBackup(TriggerType.SCHEDULED, ScheduleType.DAILY, scope, ALL, null);
    }

    @Scheduled(cron = "${backup.schedule.weekly-cron}")
    public void weeklyBackup() {
        log.info("Weekly backup triggered");
        backupService.executeBackup(TriggerType.SCHEDULED, ScheduleType.WEEKLY, BackupScope.INCREMENTAL, ALL, null);
    }

    @Scheduled(cron = "${backup.schedule.monthly-cron}")
    public void monthlyBackup() {
        log.info("Monthly FULL backup triggered");
        backupService.executeBackup(TriggerType.SCHEDULED, ScheduleType.MONTHLY, BackupScope.FULL, ALL, null);
    }
}
