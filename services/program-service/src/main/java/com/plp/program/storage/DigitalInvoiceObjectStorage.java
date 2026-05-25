package com.plp.program.storage;

import com.plp.program.config.MinioStorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;

/**
 * Optional MinIO uploads. Infrastructure exposes MinIO via docker-compose; program-service wires it only when enabled.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DigitalInvoiceObjectStorage {

    public enum UploadAttempt {
        UPLOADED,
        MINIO_DISABLED,
        MINIO_MISCONFIGURED,
        UPLOAD_FAILED
    }

    private final MinioStorageProperties props;

    public UploadAttempt tryUpload(String objectKey, byte[] bytes, String contentType) {
        if (!props.isEnabled()) {
            log.debug("plp.storage.minio.enabled=false — metadata-only digital invoice path for key {}", objectKey);
            return UploadAttempt.MINIO_DISABLED;
        }
        if (props.getAccessKey() == null || props.getAccessKey().isBlank()
                || props.getSecretKey() == null || props.getSecretKey().isBlank()) {
            log.warn("MinIO enabled but access-key or secret-key empty — skipping upload for {}", objectKey);
            return UploadAttempt.MINIO_MISCONFIGURED;
        }
        try {
            MinioClient client = MinioClient.builder()
                    .endpoint(props.getEndpoint())
                    .credentials(props.getAccessKey(), props.getSecretKey())
                    .build();
            String bucket = props.getBucket();
            ensureBucket(client, bucket);
            String ct = contentType != null && !contentType.isBlank() ? contentType : "application/octet-stream";
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                    .contentType(ct)
                    .build());
            log.info("Stored digital invoice object bucket={} key={}", bucket, objectKey);
            return UploadAttempt.UPLOADED;
        } catch (Exception e) {
            log.warn("MinIO putObject failed key={}: {}", objectKey, e.getMessage());
            return UploadAttempt.UPLOAD_FAILED;
        }
    }

    /** Download object bytes when MinIO is enabled and configured; empty if missing or error. */
    public Optional<byte[]> tryDownload(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return Optional.empty();
        }
        if (!props.isEnabled()) {
            log.debug("MinIO disabled — cannot download {}", objectKey);
            return Optional.empty();
        }
        if (props.getAccessKey() == null || props.getAccessKey().isBlank()
                || props.getSecretKey() == null || props.getSecretKey().isBlank()) {
            log.warn("MinIO enabled but credentials missing — cannot download {}", objectKey);
            return Optional.empty();
        }
        try {
            MinioClient client = MinioClient.builder()
                    .endpoint(props.getEndpoint())
                    .credentials(props.getAccessKey(), props.getSecretKey())
                    .build();
            String bucket = props.getBucket();
            try (InputStream in = client.getObject(
                    GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
                return Optional.of(in.readAllBytes());
            }
        } catch (Exception e) {
            log.warn("MinIO getObject failed key={}: {}", objectKey, e.getMessage());
            return Optional.empty();
        }
    }

    private static void ensureBucket(MinioClient client, String bucket) throws Exception {
        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }
}
