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
 * Manejador global de excepciones para la aplicación procesadora de documentos.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UnsupportedDocumentTypeException.class)
    public ResponseEntity<ApiResponse<Object>> handleUnsupportedDocumentType(UnsupportedDocumentTypeException e) {
        log.warn("Tipo de documento no soportado: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("UNSUPPORTED_DOCUMENT_TYPE", e.getMessage()));
    }

    @ExceptionHandler(DocumentValidationException.class)
    public ResponseEntity<ApiResponse<Object>> handleDocumentValidation(DocumentValidationException e) {
        log.warn("La validación del documento falló: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("VALIDATION_ERROR", e.getMessage(),
                        Map.of("validationErrors", e.getValidationErrors())));
    }

    @ExceptionHandler(StrategyNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleStrategyNotFound(StrategyNotFoundException e) {
        log.warn("Estrategia no encontrada: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("STRATEGY_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(DocumentProcessingException.class)
    public ResponseEntity<ApiResponse<Object>> handleDocumentProcessing(DocumentProcessingException e) {
        log.error("Error en el procesamiento del documento: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("PROCESSING_ERROR", e.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("Tamaño de archivo excedido: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("FILE_TOO_LARGE", "El archivo excede el tamaño máximo permitido"));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolation(ConstraintViolationException e) {
        log.warn("Violación de restricción: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("CONSTRAINT_VIOLATION", "Restricción de validación violada"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGeneral(Exception e) {
        log.error("Error inesperado: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "Ocurrió un error inesperado"));
    }
}