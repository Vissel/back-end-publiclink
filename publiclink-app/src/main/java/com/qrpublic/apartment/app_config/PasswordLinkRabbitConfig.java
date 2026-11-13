package com.qrpublic.apartment.app_config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Broker topology for password setup/reset link events. Boot's auto-config
 * picks the JSON converter up for both the RabbitTemplate and the listener
 * container, so producer and consumer speak JSON on this queue.
 */
@Configuration
public class PasswordLinkRabbitConfig {

    public static final String EXCHANGE = "publiclink.events";
    public static final String ROUTING_KEY = "buyer.password.link";
    public static final String QUEUE = "buyer.password.link.queue";

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public TopicExchange publiclinkEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue passwordLinkQueue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    @Bean
    public Binding passwordLinkBinding(Queue passwordLinkQueue, TopicExchange publiclinkEventsExchange) {
        return BindingBuilder.bind(passwordLinkQueue).to(publiclinkEventsExchange).with(ROUTING_KEY);
    }
}
