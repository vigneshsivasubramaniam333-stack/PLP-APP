package com.plp.notification.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String NOTIFICATION_EXCHANGE = "plp.notifications";
    public static final String NOTIFICATION_QUEUE = "plp.notifications.queue";
    public static final String AUDIT_EXCHANGE = "plp.audit";
    public static final String AUDIT_QUEUE = "plp.audit.queue";
    public static final String LOAN_EVENT_EXCHANGE = "plp.loan.events";
    public static final String LOAN_EVENT_QUEUE = "plp.loan.events.notification";

    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange(NOTIFICATION_EXCHANGE);
    }

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE).build();
    }

    @Bean
    public Binding notificationBinding(Queue notificationQueue, TopicExchange notificationExchange) {
        return BindingBuilder.bind(notificationQueue).to(notificationExchange).with("notification.#");
    }

    @Bean
    public TopicExchange loanEventExchange() {
        return new TopicExchange(LOAN_EVENT_EXCHANGE);
    }

    @Bean
    public Queue loanEventQueue() {
        return QueueBuilder.durable(LOAN_EVENT_QUEUE).build();
    }

    @Bean
    public Binding loanEventBinding(Queue loanEventQueue, TopicExchange loanEventExchange) {
        return BindingBuilder.bind(loanEventQueue).to(loanEventExchange).with("loan.#");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
