package com.leonardtrinh.supportsaas.common;

import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Handles all custom domain exceptions — single entry point for AppException hierarchy
    @ExceptionHandler(AppException.class)
    public ProblemDetail handleAppException(AppException ex) {
        log.warn("app_exception errorCode={} status={} message={}", ex.getErrorCode(), ex.getStatus(), ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        problem.setType(ex.getProblemType());
        problem.setProperty("errorCode", ex.getErrorCode());
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationException(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Validation Failed");
        problem.setType(URI.create("https://problems.supportsaas.io/validation-error"));
        return problem;
    }

    // Fallback for JPA EntityNotFoundException not wrapped in an AppException subclass
    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail handleEntityNotFoundException(EntityNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Resource Not Found");
        problem.setType(URI.create("https://problems.supportsaas.io/not-found"));
        return problem;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDeniedException(AccessDeniedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "You do not have permission to perform this action.");
        problem.setTitle("Access Denied");
        problem.setType(URI.create("https://problems.supportsaas.io/access-denied"));
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        log.error("unhandled_exception", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
        problem.setTitle("Internal Server Error");
        problem.setType(URI.create("https://problems.supportsaas.io/internal-error"));
        return problem;
    }
}
