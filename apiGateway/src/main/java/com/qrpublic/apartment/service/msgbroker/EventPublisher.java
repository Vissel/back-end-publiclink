package com.qrpublic.apartment.service.msgbroker;

import com.qrpublic.apartment.adapter.msgbroker.model.MessageEvent;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class EventPublisher {
    private final RabbitTemplate rabbitTemplate;

    public EventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishTransactionEvent(MessageEvent event) {
        CorrelationData correlationData = new CorrelationData(event.getMessageId());
//        correlationData.setReturned(new ReturnedMessage()); // optional
        rabbitTemplate.convertAndSend(
                "transaction.exchange",
                "transaction.created.routingKey",
                event,
                message -> {
                    message.getMessageProperties().setMessageId(event.getMessageId());
                    return message;
                },
                correlationData
        );
    }
}

