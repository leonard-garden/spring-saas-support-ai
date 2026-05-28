package com.leonardtrinh.supportsaas.storage;

import io.minio.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Slf4j
@Service
public class MinioServiceImpl implements MinioService {

    private final MinioClient minioClient;
    private final MinioProperties props;
    private final boolean initOnStartup;

    public MinioServiceImpl(MinioClient minioClient, MinioProperties props,
                            @Value("${minio.init-on-startup:true}") boolean initOnStartup) {
        this.minioClient = minioClient;
        this.props = props;
        this.initOnStartup = initOnStartup;
    }

    @PostConstruct
    void ensureBucketExists() {
        if (!initOnStartup) {
            log.info("MinIO bucket init skipped (minio.init-on-startup=false)");
            return;
        }
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(props.bucket()).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(props.bucket()).build());
                log.info("Created MinIO bucket: {}", props.bucket());
            }
        } catch (Exception e) {
            throw new MinioOperationException("Failed to ensure bucket exists: " + props.bucket(), e);
        }
    }

    @Override
    public void upload(String objectKey, InputStream stream, long size, String contentType) {
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(props.bucket())
                            .object(objectKey)
                            .stream(stream, size, -1)
                            .contentType(contentType)
                            .build()
            );
        } catch (Exception e) {
            throw new MinioOperationException("Failed to upload object: " + objectKey, e);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(props.bucket())
                            .object(objectKey)
                            .build()
            );
        } catch (Exception e) {
            throw new MinioOperationException("Failed to delete object: " + objectKey, e);
        }
    }

    @Override
    public InputStream download(String objectKey) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(props.bucket())
                            .object(objectKey)
                            .build()
            );
        } catch (Exception e) {
            throw new MinioOperationException("Failed to download object: " + objectKey, e);
        }
    }
}
