package com.qrpublic.apartment.service.msgconsumer;

import com.qrpublic.apartment.adapter.msgbroker.model.MessageEvent;
import com.qrpublic.apartment.service.IdempotencyService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class EventListener {
    @Autowired
    IdempotencyService idempotencyService;

    //    @RabbitListener(queues = "transaction.queue", containerFactory = "rabbitListenerContainerFactory")
    public void handleTransactionEvent(MessageEvent event, Channel channel,
                                       @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try {
            // 1. Idempotency check (e.g., check if messageId already processed)
            if (idempotencyService.isProcessed(event.getMessageId())) {
                channel.basicAck(tag, false);
                return;
            }

            // 2. Business logic (e.g., update balance, validate)
            processTransaction(event);

            // 3. Mark as processed
            idempotencyService.markProcessed(event.getMessageId());

            // 4. Acknowledge
            channel.basicAck(tag, false);
        } catch (Exception e) {
            // Log error
            log.error("Failed to process message: {}", event, e);
            // Reject and send to DLQ (do not requeue)
            channel.basicReject(tag, false);
        }
    }

    public void processTransaction(MessageEvent event) {
        log.info("Listener:{}", event);
    }
}
