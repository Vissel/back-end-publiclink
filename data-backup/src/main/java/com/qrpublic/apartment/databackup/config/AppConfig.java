package com.qrpublic.apartment.databackup.config;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {
    @Bean public RestTemplate restTemplate() { return new RestTemplate(); }
    @Bean public TopicExchange backupExchange() { return new TopicExchange("backup.exchange"); }
    @Bean public Queue backupTriggerQueue() { return QueueBuilder.durable("backup.trigger.queue").build(); }
    @Bean public Queue backupStatusQueue() { return QueueBuilder.durable("backup.status.queue").build(); }
    @Bean public Binding triggerBinding(Queue q, TopicExchange e) { return BindingBuilder.bind(q).to(e).with("backup.trigger.#"); }
    @Bean public Binding statusCompletedBinding(Queue q, TopicExchange e) { return BindingBuilder.bind(q).to(e).with("backup.status.completed"); }
    @Bean public Binding statusFailedBinding(Queue q, TopicExchange e) { return BindingBuilder.bind(q).to(e).with("backup.status.failed"); }
}
