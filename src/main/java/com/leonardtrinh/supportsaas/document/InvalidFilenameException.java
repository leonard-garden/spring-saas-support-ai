package com.leonardtrinh.supportsaas.document;

import com.leonardtrinh.supportsaas.common.AppException;
import org.springframework.http.HttpStatus;

public class InvalidFilenameException extends AppException {
    public InvalidFilenameException(String filename) {
        super(HttpStatus.BAD_REQUEST, "INVALID_FILENAME",
                "Filename contains illegal path traversal characters: " + filename);
    }
}
