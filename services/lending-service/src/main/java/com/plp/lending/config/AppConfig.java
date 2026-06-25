package com.plp.lending.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /** Plain HTTP client for external hosts (PayU); must not use Eureka load balancing. */
    @Bean
    public RestTemplate externalRestTemplate() {
        return new RestTemplate();
    }
}
