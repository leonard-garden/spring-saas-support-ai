package com.leonardtrinh.supportsaas.document;

import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.UUID;

public interface DocumentService {
    DocumentResponse upload(MultipartFile file);
    List<DocumentResponse> listAll(DocumentStatus status);
    DocumentResponse getById(UUID id);
    void delete(UUID id);
}
