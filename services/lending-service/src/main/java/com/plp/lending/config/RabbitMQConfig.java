package com.plp.lending.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String LOAN_EVENT_EXCHANGE = "plp.loan.events";
    public static final String AUDIT_EXCHANGE = "plp.audit";

    @Bean
    public TopicExchange loanEventExchange() {
        return new TopicExchange(LOAN_EVENT_EXCHANGE);
    }

    @Bean
    public TopicExchange auditExchange() {
        return new TopicExchange(AUDIT_EXCHANGE);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
