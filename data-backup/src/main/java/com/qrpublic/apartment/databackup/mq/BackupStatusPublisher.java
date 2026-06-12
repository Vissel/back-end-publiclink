package com.qrpublic.apartment.databackup.mq;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qrpublic.apartment.databackup.model.BackupResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

@Component @RequiredArgsConstructor @Slf4j
public class BackupStatusPublisher {
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public void publishCompleted(BackupResult r) { publish("backup.status.completed", r); }
    public void publishFailed(BackupResult r) { publish("backup.status.failed", r); }

    private void publish(String key, BackupResult r) {
        try {
            Map<String, Object> s = new HashMap<>();
            s.put("triggerId", r.getTriggerId()); s.put("status", r.getStatus()); s.put("scope", r.getScope());
            s.put("startedAt", r.getStartedAt()); s.put("completedAt", r.getCompletedAt());
            s.put("durationSeconds", r.getDurationSeconds()); s.put("totalFileSizeBytes", r.getTotalFileSizeBytes());
            s.put("errorMessage", r.getErrorMessage());
            rabbitTemplate.convertAndSend("backup.exchange", key, objectMapper.writeValueAsString(s));
            log.info("Status published: {} -> {}", key, r.getTriggerId());
        } catch (Exception e) { log.error("Status publish failed: {}", e.getMessage()); }
    }
}
