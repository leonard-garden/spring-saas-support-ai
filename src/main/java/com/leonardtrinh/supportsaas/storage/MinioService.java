package com.leonardtrinh.supportsaas.storage;

import java.io.InputStream;

public interface MinioService {
    void upload(String objectKey, InputStream stream, long size, String contentType);
    void delete(String objectKey);
    InputStream download(String objectKey);
}
