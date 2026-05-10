package com.leonardtrinh.supportsaas.common;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends AppException {

    public ResourceNotFoundException(String resourceType, Object id) {
        super(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", resourceType + " not found: " + id);
    }
}
