package com.example.documentprocessor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessingResult {

    private String documentName;
    private String processingStrategy;
    private boolean success;
    private String message;
    private List<String> errors;
    private List<String> warnings;

    @Builder.Default
    private LocalDateTime processedAt = LocalDateTime.now();

    private Long processingTimeMs;

    // Metadatos extraídos del documento
    private DocumentMetadata metadata;

    // Contenido extraído (texto, datos, etc.)
    private String extractedText;
    private List<Map<String, Object>> extractedData; // Para datos estructurados como CSV/Excel

    // Estadísticas del procesamiento
    private Map<String, Object> processingStats;

    // Método helper para agregar errores
    public void addError(String error) {
        if (this.errors == null) {
            this.errors = new java.util.ArrayList<>();
        }
        this.errors.add(error);
        this.success = false;
    }

    // Método helper para agregar warnings
    public void addWarning(String warning) {
        if (this.warnings == null) {
            this.warnings = new java.util.ArrayList<>();
        }
        this.warnings.add(warning);
    }
}