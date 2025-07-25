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
 * Controlador REST para operaciones de procesamiento de documentos.
 * Demuestra el uso del patrón Strategy a través de endpoints HTTP.
 */
@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // Para desarrollo - ajustar en producción
public class DocumentController {

    private final DocumentProcessorService documentProcessorService;

    /**
     * Procesa un documento cargado como archivo multipart.
     * La estrategia de procesamiento se selecciona automáticamente en base a la extensión del archivo.
     *
     * @param file el archivo de documento a procesar
     * @return ProcessingResult con los datos y metadatos extraídos
     */
    @PostMapping(value = "/process", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProcessingResult>> processDocument(
            @RequestParam("file") @NotNull MultipartFile file) {

        long startTime = System.currentTimeMillis();
        log.info("Solicitud de procesamiento de documento recibida para el archivo: {} (Tamaño: {} bytes)",
                file.getOriginalFilename(), file.getSize());

        try {
            // Validar archivo
            validateMultipartFile(file);

            // Convertir a Document
            Document document = createDocumentFromMultipartFile(file);

            // Procesar documento
            ProcessingResult result = documentProcessorService.processDocument(document);

            long totalTime = System.currentTimeMillis() - startTime;
            log.info("Procesamiento del documento completado en {} ms para el archivo: {}",
                    totalTime, file.getOriginalFilename());

            return ResponseEntity.ok(ApiResponse.success(result, "Documento procesado correctamente"));

        } catch (UnsupportedDocumentTypeException e) {
            log.warn("Tipo de documento no soportado: {}", e.getFileExtension(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("UNSUPPORTED_DOCUMENT_TYPE", e.getMessage(),
                            Map.of("supportedExtensions", documentProcessorService.getSupportedExtensions())));

        } catch (DocumentValidationException e) {
            log.warn("La validación del documento falló: {}", e.getValidationErrors(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VALIDATION_ERROR", e.getMessage(),
                            Map.of("validationErrors", e.getValidationErrors())));

        } catch (DocumentProcessingException e) {
            log.error("El procesamiento del documento falló: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("PROCESSING_ERROR", e.getMessage()));

        } catch (Exception e) {
            log.error("Error inesperado al procesar el documento: {}", file.getOriginalFilename(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", "Ocurrió un error inesperado"));
        }
    }

    /**
     * Procesa un documento usando una estrategia específica.
     * Útil para probar diferentes estrategias o forzar un enfoque de procesamiento en particular.
     *
     * @param file el archivo de documento a procesar
     * @param strategyName el nombre de la estrategia a utilizar
     * @return ProcessingResult con los datos y metadatos extraídos
     */
    @PostMapping(value = "/process/{strategyName}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProcessingResult>> processDocumentWithStrategy(
            @RequestParam("file") @NotNull MultipartFile file,
            @PathVariable @NotBlank String strategyName) {

        log.info("Solicitud de procesamiento de documento recibida con la estrategia '{}' para el archivo: {}",
                strategyName, file.getOriginalFilename());

        try {
            validateMultipartFile(file);
            Document document = createDocumentFromMultipartFile(file);

            ProcessingResult result = documentProcessorService.processDocumentWithStrategy(document, strategyName);

            return ResponseEntity.ok(ApiResponse.success(result,
                    "Documento procesado correctamente con la estrategia: " + strategyName));

        } catch (StrategyNotFoundException e) {
            log.warn("Estrategia no encontrada: {}", strategyName, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("STRATEGY_NOT_FOUND", e.getMessage(),
                            Map.of("availableStrategies", getAvailableStrategyNames())));

        } catch (DocumentProcessingException e) {
            log.error("Falló el procesamiento del documento con la estrategia {}: {}", strategyName, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("PROCESSING_ERROR", e.getMessage()));

        } catch (Exception e) {
            log.error("Error inesperado al procesar el documento con la estrategia {}: {}", strategyName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", "Ocurrió un error inesperado"));
        }
    }

    /**
     * Obtener información sobre todas las estrategias de procesamiento disponibles.
     *
     * @return Mapa que contiene información sobre las estrategias disponibles
     */
    @GetMapping("/strategies")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStrategiesInfo() {
        log.debug("Obteniendo información de las estrategias");

        try {
            Map<String, Object> strategiesInfo = documentProcessorService.getStrategiesInfo();
            return ResponseEntity.ok(ApiResponse.success(strategiesInfo, "Información de estrategias recuperada"));

        } catch (Exception e) {
            log.error("Error al recuperar información de estrategias", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", "Error al recuperar información de estrategias"));
        }
    }

    /**
     * Verificar si una extensión de archivo específica está soportada.
     *
     * @param extension la extensión del archivo a verificar (con o sin punto)
     * @return booleano que indica si la extensión está soportada
     */
    @GetMapping("/supported-extensions/{extension}")
    public ResponseEntity<ApiResponse<Boolean>> isExtensionSupported(@PathVariable String extension) {
        log.debug("Verificando si la extensión '{}' está soportada", extension);


        try {
            boolean isSupported = documentProcessorService.isFileExtensionSupported(extension);
            String message = isSupported ?
                    "La extensión está soportada" :
                    "La extensión no está soportada";

            Map<String, Object> additionalData = new HashMap<>();
            additionalData.put("extension", extension);
            if (!isSupported) {
                additionalData.put("extensionesSoportadas", documentProcessorService.getSupportedExtensions());
            }

            return ResponseEntity.ok(ApiResponse.success(isSupported, message, additionalData));

        } catch (Exception e) {
            log.error("Error al verificar si la extensión está soportada: {}", extension, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", "Error al verificar soporte de extensión"));
        }
    }

    /**
     * Obtiene todas las extensiones de archivo soportadas.
     *
     * @return Conjunto de extensiones soportadas
     */
    @GetMapping("/supported-extensions")
    public ResponseEntity<ApiResponse<Set<String>>> getSupportedExtensions() {
        log.debug("Obteniendo todas las extensiones soportadas");

        try {
            Set<String> extensions = documentProcessorService.getSupportedExtensions();
            return ResponseEntity.ok(ApiResponse.success(extensions, "Extensiones soportadas recuperadas"));

        } catch (Exception e) {
            log.error("Error al recuperar las extensiones soportadas", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", "Error al recuperar extensiones soportadas"));
        }
    }

    /**
     * Obtiene estadísticas de procesamiento e información del sistema.
     *
     * @return Mapa con estadísticas de procesamiento
     */
    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProcessingStatistics() {
        log.debug("Obteniendo estadísticas de procesamiento");

        try {
            Map<String, Object> statistics = documentProcessorService.getProcessingStatistics();
            return ResponseEntity.ok(ApiResponse.success(statistics, "Estadísticas de procesamiento recuperadas"));

        } catch (Exception e) {
            log.error("Error al recuperar estadísticas de procesamiento", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", "Error al recuperar estadísticas de procesamiento"));
        }
    }

    /**
     * Endpoint de verificación de estado del servicio (Health check).
     *
     * @return Estado de salud simple del servicio
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("estado", "ACTIVO");
        health.put("timestamp", LocalDateTime.now());
        health.put("estrategiasDisponibles", getAvailableStrategyNames().size());
        health.put("extensionesSoportadas", documentProcessorService.getSupportedExtensions().size());

        return ResponseEntity.ok(ApiResponse.success(health, "El servicio está funcionando correctamente"));
    }

    // Métodos privados de ayuda

    private void validateMultipartFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new DocumentValidationException("El archivo no puede estar vacío");
        }

        if (file.getOriginalFilename() == null || file.getOriginalFilename().trim().isEmpty()) {
            throw new DocumentValidationException("El archivo debe tener un nombre de archivo válido");
        }

        // Validar tamaño máximo (100MB por ejemplo)
        long maxSize = 100 * 1024 * 1024; // 100MB
        if (file.getSize() > maxSize) {
            throw new DocumentValidationException(
                    String.format("El tamaño del archivo (%d bytes) excede el tamaño máximo permitido (%d bytes)",
                            file.getSize(), maxSize));
        }

        // Validar que tenga extensión
        String filename = file.getOriginalFilename();
        if (!filename.contains(".")) {
            throw new DocumentValidationException("El archivo debe tener una extensión válida");
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
            return "DESCONOCIDO";
        }

        String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        return switch (extension) {
            case "pdf" -> "PDF";
            case "xlsx", "xls" -> "EXCEL";
            case "docx", "doc" -> "WORD";
            case "csv" -> "CSV";
            default -> "DESCONOCIDO";
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
     * Contenedor genérico de respuestas API para un formato de respuesta consistente.
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