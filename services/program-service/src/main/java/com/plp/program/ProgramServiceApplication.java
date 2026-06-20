package com.plp.program;

import com.plp.program.config.MinioStorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
@EnableConfigurationProperties(MinioStorageProperties.class)
public class ProgramServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProgramServiceApplication.class, args);
    }
}
