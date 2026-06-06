package com.leonardtrinh.supportsaas.config;

import com.leonardtrinh.supportsaas.storage.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("minio")
public class MinioHealthIndicator implements HealthIndicator {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    public MinioHealthIndicator(MinioClient minioClient, MinioProperties minioProperties) {
        this.minioClient = minioClient;
        this.minioProperties = minioProperties;
    }

    @Override
    public Health health() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(minioProperties.bucket()).build());
            if (exists) {
                return Health.up()
                        .withDetail("bucket", minioProperties.bucket())
                        .withDetail("endpoint", minioProperties.endpoint())
                        .build();
            } else {
                return Health.down()
                        .withDetail("bucket", minioProperties.bucket())
                        .withDetail("reason", "bucket not found")
                        .build();
            }
        } catch (Exception e) {
            return Health.down(e)
                    .withDetail("endpoint", minioProperties.endpoint())
                    .build();
        }
    }
}
