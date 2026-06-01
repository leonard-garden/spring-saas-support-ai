package com.leonardtrinh.supportsaas.chatbot;

import com.leonardtrinh.supportsaas.common.AppException;
import org.springframework.http.HttpStatus;

public class WidgetAlreadyExistsException extends AppException {
    public WidgetAlreadyExistsException() {
        super(HttpStatus.CONFLICT, "WIDGET_ALREADY_EXISTS", "A widget already exists for this tenant");
    }
}
