package com.example.documentprocessor.controller;

import com.example.documentprocessor.exception.DocumentProcessingException;
import com.example.documentprocessor.exception.DocumentValidationException;
import com.example.documentprocessor.exception.StrategyNotFoundException;
import com.example.documentprocessor.exception.UnsupportedDocumentTypeException;
import com.example.documentprocessor.model.Document;
import com.example.documentprocessor.model.ProcessingResult;
import com.example.documentprocessor.service.DocumentProcessorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

/**
 * REST Controller for document processing operations.
 * Demonstrates the Strategy pattern in action through HTTP endpoints.
 */
@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // Para desarrollo - ajustar en producción
public class DocumentController {

    private final DocumentProcessorService documentProcessorService;

    /**
     * Process a document uploaded as multipart file.
     * The processing strategy is automatically selected based on file extension.
     *
     * @param file the document file to process
     * @return ProcessingResult with extracted data and metadata
     */
    @PostMapping(value = "/process", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProcessingResult>> processDocument(
            @RequestParam("file") @NotNull MultipartFile file) {

        long startTime = System.currentTimeMillis();
        log.info("Received document processing request for file: {} (Size: {} bytes)",
                file.getOriginalFilename(), file.getSize());

        try {
            // Validar archivo
            validateMultipartFile(file);

            // Convertir a Document
            Document document = createDocumentFromMultipartFile(file);

            // Procesar documento
            ProcessingResult result = documentProcessorService.processDocument(document);

            long totalTime = System.currentTimeMillis() - startTime;
            log.info("Document processing completed in {} ms for file: {}", totalTime, file.getOriginalFilename());

            return ResponseEntity.ok(ApiResponse.success(result, "Document processed successfully"));

        } catch (UnsupportedDocumentTypeException e) {
            log.warn("Unsupported document type: {}", e.getFileExtension(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("UNSUPPORTED_DOCUMENT_TYPE", e.getMessage(),
                            Map.of("supportedExtensions", documentProcessorService.getSupportedExtensions())));

        } catch (DocumentValidationException e) {
            log.warn("Document validation failed: {}", e.getValidationErrors(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VALIDATION_ERROR", e.getMessage(),
                            Map.of("validationErrors", e.getValidationErrors())));

        } catch (DocumentProcessingException e) {
            log.error("Document processing failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("PROCESSING_ERROR", e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error processing document: {}", file.getOriginalFilename(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred"));
        }
    }

    /**
     * Process a document using a specific strategy.
     * Useful for testing different strategies or forcing a particular processing approach.
     *
     * @param file the document file to process
     * @param strategyName the name of the strategy to use
     * @return ProcessingResult with extracted data and metadata
     */
    @PostMapping(value = "/process/{strategyName}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProcessingResult>> processDocumentWithStrategy(
            @RequestParam("file") @NotNull MultipartFile file,
            @PathVariable @NotBlank String strategyName) {

        log.info("Received document processing request with strategy '{}' for file: {}",
                strategyName, file.getOriginalFilename());

        try {
            validateMultipartFile(file);
            Document document = createDocumentFromMultipartFile(file);

            ProcessingResult result = documentProcessorService.processDocumentWithStrategy(document, strategyName);

            return ResponseEntity.ok(ApiResponse.success(result,
                    "Document processed successfully with strategy: " + strategyName));

        } catch (StrategyNotFoundException e) {
            log.warn("Strategy not found: {}", strategyName, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("STRATEGY_NOT_FOUND", e.getMessage(),
                            Map.of("availableStrategies", getAvailableStrategyNames())));

        } catch (DocumentProcessingException e) {
            log.error("Document processing failed with strategy {}: {}", strategyName, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("PROCESSING_ERROR", e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error processing document with strategy {}: {}", strategyName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred"));
        }
    }

    /**
     * Get information about all available processing strategies.
     *
     * @return Map containing information about available strategies
     */
    @GetMapping("/strategies")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStrategiesInfo() {
        log.debug("Fetching strategies information");

        try {
            Map<String, Object> strategiesInfo = documentProcessorService.getStrategiesInfo();
            return ResponseEntity.ok(ApiResponse.success(strategiesInfo, "Strategies information retrieved"));

        } catch (Exception e) {
            log.error("Error retrieving strategies information", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", "Error retrieving strategies information"));
        }
    }

    /**
     * Check if a specific file extension is supported.
     *
     * @param extension the file extension to check (with or without dot)
     * @return boolean indicating if the extension is supported
     */
    @GetMapping("/supported-extensions/{extension}")
    public ResponseEntity<ApiResponse<Boolean>> isExtensionSupported(@PathVariable String extension) {
        log.debug("Checking if extension '{}' is supported", extension);

        try {
            boolean isSupported = documentProcessorService.isFileExtensionSupported(extension);
            String message = isSupported ?
                    "Extension is supported" :
                    "Extension is not supported";

            Map<String, Object> additionalData = new HashMap<>();
            additionalData.put("extension", extension);
            if (!isSupported) {
                additionalData.put("supportedExtensions", documentProcessorService.getSupportedExtensions());
            }

            return ResponseEntity.ok(ApiResponse.success(isSupported, message, additionalData));

        } catch (Exception e) {
            log.error("Error checking extension support for: {}", extension, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", "Error checking extension support"));
        }
    }

    /**
     * Get all supported file extensions.
     *
     * @return Set of supported file extensions
     */
    @GetMapping("/supported-extensions")
    public ResponseEntity<ApiResponse<Set<String>>> getSupportedExtensions() {
        log.debug("Fetching all supported extensions");

        try {
            Set<String> extensions = documentProcessorService.getSupportedExtensions();
            return ResponseEntity.ok(ApiResponse.success(extensions, "Supported extensions retrieved"));

        } catch (Exception e) {
            log.error("Error retrieving supported extensions", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", "Error retrieving supported extensions"));
        }
    }

    /**
     * Get processing statistics and system information.
     *
     * @return Map containing processing statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProcessingStatistics() {
        log.debug("Fetching processing statistics");

        try {
            Map<String, Object> statistics = documentProcessorService.getProcessingStatistics();
            return ResponseEntity.ok(ApiResponse.success(statistics, "Processing statistics retrieved"));

        } catch (Exception e) {
            log.error("Error retrieving processing statistics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", "Error retrieving processing statistics"));
        }
    }

    /**
     * Health check endpoint to verify the service is running.
     *
     * @return Simple health status
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now());
        health.put("availableStrategies", getAvailableStrategyNames().size());
        health.put("supportedExtensions", documentProcessorService.getSupportedExtensions().size());

        return ResponseEntity.ok(ApiResponse.success(health, "Service is healthy"));
    }

    // Métodos privados de ayuda

    private void validateMultipartFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new DocumentValidationException("File cannot be empty");
        }

        if (file.getOriginalFilename() == null || file.getOriginalFilename().trim().isEmpty()) {
            throw new DocumentValidationException("File must have a valid filename");
        }

        // Validar tamaño máximo (100MB por ejemplo)
        long maxSize = 100 * 1024 * 1024; // 100MB
        if (file.getSize() > maxSize) {
            throw new DocumentValidationException(
                    String.format("File size (%d bytes) exceeds maximum allowed size (%d bytes)",
                            file.getSize(), maxSize));
        }

        // Validar que tenga extensión
        String filename = file.getOriginalFilename();
        if (!filename.contains(".")) {
            throw new DocumentValidationException("File must have a valid extension");
        }
    }

    private Document createDocumentFromMultipartFile(MultipartFile file) throws IOException {
        return Document.builder()
                .name(file.getOriginalFilename())
                .content(file.getBytes())
                .size(file.getSize())
                .type(determineTypeFromFilename(file.getOriginalFilename()))
                .build();
    }

    private String determineTypeFromFilename(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "UNKNOWN";
        }

        String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        return switch (extension) {
            case "pdf" -> "PDF";
            case "xlsx", "xls" -> "EXCEL";
            case "docx", "doc" -> "WORD";
            case "csv" -> "CSV";
            default -> "UNKNOWN";
        };
    }

    private List<String> getAvailableStrategyNames() {
        return documentProcessorService.getStrategiesInfo().get("strategies") != null ?
                ((List<Map<String, Object>>) documentProcessorService.getStrategiesInfo().get("strategies"))
                        .stream()
                        .map(strategy -> (String) strategy.get("name"))
                        .toList() :
                List.of();
    }

    /**
     * Generic API Response wrapper for consistent response format.
     */
    public static class ApiResponse<T> {
        public boolean success;
        public String message;
        public T data;
        public String errorCode;
        public Map<String, Object> metadata;
        public LocalDateTime timestamp;

        public ApiResponse() {
            this.timestamp = LocalDateTime.now();
        }

        public static <T> ApiResponse<T> success(T data, String message) {
            ApiResponse<T> response = new ApiResponse<>();
            response.success = true;
            response.message = message;
            response.data = data;
            return response;
        }

        public static <T> ApiResponse<T> success(T data, String message, Map<String, Object> metadata) {
            ApiResponse<T> response = success(data, message);
            response.metadata = metadata;
            return response;
        }

        public static <T> ApiResponse<T> error(String errorCode, String message) {
            ApiResponse<T> response = new ApiResponse<>();
            response.success = false;
            response.errorCode = errorCode;
            response.message = message;
            return response;
        }

        public static <T> ApiResponse<T> error(String errorCode, String message, Map<String, Object> metadata) {
            ApiResponse<T> response = error(errorCode, message);
            response.metadata = metadata;
            return response;
        }
    }
}