package com.plp.notification.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "plp.notification.email")
public class EmailNotificationProperties {

    private String fromAddress = "billiontechuat@gmail.com";
    private String fromName = "PLP Platform";
    private boolean simulationEnabled = false;
}
