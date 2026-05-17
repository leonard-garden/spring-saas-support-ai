package com.leonardtrinh.supportsaas.storage;

import io.minio.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
public class MinioServiceImpl implements MinioService {

    private static final Logger log = LoggerFactory.getLogger(MinioServiceImpl.class);

    private final MinioClient minioClient;
    private final MinioProperties props;

    public MinioServiceImpl(MinioClient minioClient, MinioProperties props) {
        this.minioClient = minioClient;
        this.props = props;
    }

    @PostConstruct
    void ensureBucketExists() {
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
