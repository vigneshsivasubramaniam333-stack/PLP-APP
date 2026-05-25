package com.plp.program.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "plp.storage.minio")
public class MinioStorageProperties {

    /** When false (default), digital invoices persist metadata only; no bytes sent to MinIO. */
    private boolean enabled = false;

    private String endpoint = "http://localhost:9000";

    private String accessKey = "";

    private String secretKey = "";

    private String bucket = "plp-invoices";
}
