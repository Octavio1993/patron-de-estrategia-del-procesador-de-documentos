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
 * Clase de configuración para los ajustes de procesamiento de documentos.
 * Mapea las propiedades de la aplicación y proporciona beans de configuración.
 */
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "document-processor")
@Validated
@Data
public class DocumentConfig {

    /**
     * Configuración de límites de procesamiento
     */
    @NotNull
    private Limits limits = new Limits();

    /**
     * Configuraciones específicas por tipo de documento
     */
    @NotNull
    private Map<String, DocumentTypeConfig> documentTypes = Map.of(
            "pdf", new DocumentTypeConfig(),
            "excel", new DocumentTypeConfig(),
            "word", new DocumentTypeConfig(),
            "csv", new DocumentTypeConfig()
    );

    /**
     * Configuración de estadísticas
     */
    @NotNull
    private Statistics statistics = new Statistics();

    /**
     * Configuración de rendimiento
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
     * Bean para el servicio ejecutor si el procesamiento asíncrono está habilitado
     */
    @Bean
    public ExecutorService documentProcessingExecutor() {
        if (performance.getAsyncProcessing()) {
            log.info("Creando un pool de hilos con {} hilos para el procesamiento asíncrono de documentos",
                    performance.getThreadPoolSize());
            return Executors.newFixedThreadPool(performance.getThreadPoolSize());
        } else {
            log.info("Procesamiento asíncrono deshabilitado, usando procesamiento sincrónico");
            return Executors.newSingleThreadExecutor(); // Fallback minimal executor
        }
    }

    /**
     * Obtener configuración para un tipo de documento específico
     */
    public DocumentTypeConfig getConfigForType(String documentType) {
        return documentTypes.getOrDefault(documentType.toLowerCase(), new DocumentTypeConfig());
    }

    /**
     * Verificar si el tamaño de archivo está dentro de los límites
     */
    public boolean isFileSizeAllowed(long fileSizeBytes) {
        return fileSizeBytes <= limits.getMaxFileSizeBytes();
    }

    /**
     * Verificar si la longitud del texto está dentro de los límites
     */
    public boolean isTextLengthAllowed(int textLength) {
        return textLength <= limits.getMaxTextLength();
    }

    /**
     * Obtener el tiempo máximo de procesamiento permitido en milisegundos
     */
    public long getMaxProcessingTimeMs() {
        return limits.getMaxProcessingTimeMs();
    }

    /**
     * Validación y registro de la configuración
     */
    @jakarta.annotation.PostConstruct
    public void logConfiguration() {
        log.info("Configuración del Procesador de Documentos:");
        log.info("  Tamaño máximo de archivo: {} MB", limits.getMaxFileSizeBytes() / (1024 * 1024));
        log.info("  Tiempo máximo de procesamiento: {} minutos", limits.getMaxProcessingTimeMs() / 60000);
        log.info("  Longitud máxima de texto: {} caracteres", limits.getMaxTextLength());
        log.info("  Máximo de filas en tabla: {}", limits.getMaxTableRows());
        log.info("  Máximo de hojas en Excel: {}", limits.getMaxExcelSheets());
        log.info("  Análisis de frecuencia de palabras: {}", statistics.getEnableWordFrequency());
        log.info("  Detección de idioma: {}", statistics.getEnableLanguageDetection());
        log.info("  Procesamiento asíncrono: {}", performance.getAsyncProcessing());
        log.info("  Caché habilitada: {}", performance.getEnableCaching());

        // Registrar configuraciones por tipo de documento
        documentTypes.forEach((type, config) -> {
            log.debug("  {}: {}", type, config);
        });
    }
}