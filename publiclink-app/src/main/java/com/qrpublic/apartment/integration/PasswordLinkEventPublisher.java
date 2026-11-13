package com.qrpublic.apartment.integration;

import com.qrpublic.apartment.adapter.msgbroker.model.PasswordLinkEvent;
import com.qrpublic.apartment.app_config.PasswordLinkRabbitConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Publishes password setup/reset link events. Never throws: when the broker
 * is unreachable the caller still has the token row and the link (returned in
 * the API response and copyable in the UI), so delivery degrades to manual
 * forwarding instead of failing plan creation.
 */
@Slf4j
@Component
public class PasswordLinkEventPublisher {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void publish(PasswordLinkEvent event) {
        if (event.getMessageId() == null) {
            event.setMessageId(UUID.randomUUID().toString());
        }
        if (event.getTimestamp() == null) {
            event.setTimestamp(LocalDateTime.now());
        }
        try {
            rabbitTemplate.convertAndSend(PasswordLinkRabbitConfig.EXCHANGE,
                    PasswordLinkRabbitConfig.ROUTING_KEY, event);
            log.info("Published password link event [{}] for [{}]", event.getMessageId(), event.getUsername());
        } catch (Exception e) {
            log.error("Broker unavailable, password link event [{}] for [{}] NOT queued; "
                    + "the link stays copyable in the UI", event.getMessageId(), event.getUsername(), e);
        }
    }
}
