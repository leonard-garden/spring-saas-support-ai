package com.leonardtrinh.supportsaas.config;

import com.leonardtrinh.supportsaas.storage.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MinioHealthIndicatorTest {

    @Mock
    private MinioClient minioClient;

    private MinioHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        MinioProperties props = new MinioProperties(
                "http://localhost:9000", "minioadmin", "minioadmin", "test-bucket");
        indicator = new MinioHealthIndicator(minioClient, props);
    }

    @Test
    @DisplayName("health returns UP when bucket exists")
    void health_bucketExists_returnsUp() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsKey("bucket");
        assertThat(health.getDetails().get("bucket")).isEqualTo("test-bucket");
    }

    @Test
    @DisplayName("health returns DOWN when bucket does not exist")
    void health_bucketMissing_returnsDown() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("reason");
    }

    @Test
    @DisplayName("health returns DOWN when MinIO is unreachable")
    void health_connectionFailure_returnsDown() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class)))
                .thenThrow(new RuntimeException("connection refused"));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("error");
    }
}
