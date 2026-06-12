package com.qrpublic.apartment.databackup.mq;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qrpublic.apartment.databackup.entity.TriggerEntity.*;
import com.qrpublic.apartment.databackup.service.BackupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Component @RequiredArgsConstructor @Slf4j
public class BackupTriggerListener {
    private final BackupService backupService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "backup.trigger.queue")
    public void onTriggerMessage(Message message) {
        try {
            String body = new String(message.getBody());
            log.info("Received backup trigger: {}", body);
            @SuppressWarnings("unchecked") Map<String, Object> payload = objectMapper.readValue(body, Map.class);
            String scopeStr = (String) payload.getOrDefault("scope", "INCREMENTAL");
            BackupScope scope = BackupScope.valueOf(scopeStr.toUpperCase());
            @SuppressWarnings("unchecked") List<String> schemas = (List<String>) payload.get("schemas");
            backupService.executeBackup(TriggerType.MANUAL, null, scope, schemas, null);
        } catch (Exception e) { log.error("MQ trigger failed: {}", e.getMessage(), e); }
    }
}
