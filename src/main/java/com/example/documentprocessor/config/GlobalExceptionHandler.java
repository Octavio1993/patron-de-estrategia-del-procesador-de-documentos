package com.example.documentprocessor.config;

import com.example.documentprocessor.controller.DocumentController.ApiResponse;
import com.example.documentprocessor.exception.DocumentProcessingException;
import com.example.documentprocessor.exception.DocumentValidationException;
import com.example.documentprocessor.exception.StrategyNotFoundException;
import com.example.documentprocessor.exception.UnsupportedDocumentTypeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import jakarta.validation.ConstraintViolationException;
import java.util.Map;

/**
 * Global exception handler for the document processor application.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UnsupportedDocumentTypeException.class)
    public ResponseEntity<ApiResponse<Object>> handleUnsupportedDocumentType(UnsupportedDocumentTypeException e) {
        log.warn("Unsupported document type: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("UNSUPPORTED_DOCUMENT_TYPE", e.getMessage()));
    }

    @ExceptionHandler(DocumentValidationException.class)
    public ResponseEntity<ApiResponse<Object>> handleDocumentValidation(DocumentValidationException e) {
        log.warn("Document validation failed: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("VALIDATION_ERROR", e.getMessage(),
                        Map.of("validationErrors", e.getValidationErrors())));
    }

    @ExceptionHandler(StrategyNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleStrategyNotFound(StrategyNotFoundException e) {
        log.warn("Strategy not found: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("STRATEGY_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(DocumentProcessingException.class)
    public ResponseEntity<ApiResponse<Object>> handleDocumentProcessing(DocumentProcessingException e) {
        log.error("Document processing error: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("PROCESSING_ERROR", e.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("File size exceeded: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("FILE_TOO_LARGE", "File size exceeds maximum allowed size"));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolation(ConstraintViolationException e) {
        log.warn("Constraint violation: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("CONSTRAINT_VIOLATION", "Validation constraint violated"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGeneral(Exception e) {
        log.error("Unexpected error: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}