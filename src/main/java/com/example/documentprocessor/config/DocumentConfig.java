package com.example.documentprocessor.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Configuration class for document processing settings.
 * Maps application properties and provides configuration beans.
 */
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "document-processor")
@Validated
@Data
public class DocumentConfig {

    /**
     * Processing limits configuration
     */
    @NotNull
    private Limits limits = new Limits();

    /**
     * Document type specific configurations
     */
    @NotNull
    private Map<String, DocumentTypeConfig> documentTypes = Map.of(
            "pdf", new DocumentTypeConfig(),
            "excel", new DocumentTypeConfig(),
            "word", new DocumentTypeConfig(),
            "csv", new DocumentTypeConfig()
    );

    /**
     * Statistics configuration
     */
    @NotNull
    private Statistics statistics = new Statistics();

    /**
     * Performance configuration
     */
    @NotNull
    private Performance performance = new Performance();

    @Data
    public static class Limits {
        @Min(1)
        private Long maxFileSizeBytes = 104857600L; // 100MB

        @Min(1000)
        private Long maxProcessingTimeMs = 300000L; // 5 minutes

        @Min(1000)
        private Integer maxTextLength = 10000000; // 10M characters

        @Min(100)
        private Integer maxTableRows = 100000; // 100K rows

        @Min(1)
        private Integer maxExcelSheets = 50; // 50 sheets
    }

    @Data
    public static class DocumentTypeConfig {
        private Boolean extractImages = false;
        private Boolean extractForms = false;
        private Boolean extractComments = false;
        private Boolean extractRevisions = false;
        private Boolean includeHiddenSheets = false;
        private Boolean evaluateFormulas = true;
        private Boolean autoDetectDelimiter = true;
        private Boolean autoDetectEncoding = true;
        private Integer maxPages = 1000;
        private Integer maxRowsPerSheet = 100000;
        private Integer maxColumns = 1000;
    }

    @Data
    public static class Statistics {
        private Boolean enableWordFrequency = true;
        private Integer maxTopWords = 20;
        private Integer minWordLength = 3;
        private Boolean enableLanguageDetection = true;
    }

    @Data
    public static class Performance {
        private Boolean enableCaching = false;
        private Integer cacheTtlMinutes = 60;
        private Boolean asyncProcessing = false;
        private Integer threadPoolSize = 4;
    }

    /**
     * Bean for executor service if async processing is enabled
     */
    @Bean
    public ExecutorService documentProcessingExecutor() {
        if (performance.getAsyncProcessing()) {
            log.info("Creating thread pool with {} threads for async document processing",
                    performance.getThreadPoolSize());
            return Executors.newFixedThreadPool(performance.getThreadPoolSize());
        } else {
            log.info("Async processing disabled, using synchronous processing");
            return Executors.newSingleThreadExecutor(); // Fallback minimal executor
        }
    }

    /**
     * Get configuration for a specific document type
     */
    public DocumentTypeConfig getConfigForType(String documentType) {
        return documentTypes.getOrDefault(documentType.toLowerCase(), new DocumentTypeConfig());
    }

    /**
     * Check if a file size is within limits
     */
    public boolean isFileSizeAllowed(long fileSizeBytes) {
        return fileSizeBytes <= limits.getMaxFileSizeBytes();
    }

    /**
     * Check if text length is within limits
     */
    public boolean isTextLengthAllowed(int textLength) {
        return textLength <= limits.getMaxTextLength();
    }

    /**
     * Get maximum allowed processing time in milliseconds
     */
    public long getMaxProcessingTimeMs() {
        return limits.getMaxProcessingTimeMs();
    }

    /**
     * Configuration validation and logging
     */
    @jakarta.annotation.PostConstruct
    public void logConfiguration() {
        log.info("Document Processor Configuration:");
        log.info("  Max file size: {} MB", limits.getMaxFileSizeBytes() / (1024 * 1024));
        log.info("  Max processing time: {} minutes", limits.getMaxProcessingTimeMs() / 60000);
        log.info("  Max text length: {} characters", limits.getMaxTextLength());
        log.info("  Max table rows: {}", limits.getMaxTableRows());
        log.info("  Max Excel sheets: {}", limits.getMaxExcelSheets());
        log.info("  Word frequency analysis: {}", statistics.getEnableWordFrequency());
        log.info("  Language detection: {}", statistics.getEnableLanguageDetection());
        log.info("  Async processing: {}", performance.getAsyncProcessing());
        log.info("  Caching enabled: {}", performance.getEnableCaching());

        // Log document type configurations
        documentTypes.forEach((type, config) -> {
            log.debug("  {}: {}", type, config);
        });
    }
}