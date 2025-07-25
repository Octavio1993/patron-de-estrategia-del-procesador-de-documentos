package com.example.documentprocessor.service;

import com.example.documentprocessor.exception.DocumentProcessingException;
import com.example.documentprocessor.exception.StrategyNotFoundException;
import com.example.documentprocessor.exception.UnsupportedDocumentTypeException;
import com.example.documentprocessor.model.Document;
import com.example.documentprocessor.model.DocumentType;
import com.example.documentprocessor.model.ProcessingResult;
import com.example.documentprocessor.strategy.DocumentProcessingStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service that orchestrates document processing using the Strategy pattern.
 * Automatically discovers and manages all available processing strategies.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentProcessorService {

    private final List<DocumentProcessingStrategy> strategies;
    private Map<DocumentType, DocumentProcessingStrategy> strategyMap;
    private Map<String, DocumentProcessingStrategy> strategyNameMap;

    @PostConstruct
    public void initializeStrategies() {
        log.info("Initializing document processing strategies...");

        // Crear mapa por tipo de documento, priorizando por priority()
        strategyMap = strategies.stream()
                .collect(Collectors.groupingBy(DocumentProcessingStrategy::getSupportedType))
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .min(Comparator.comparingInt(DocumentProcessingStrategy::getPriority))
                                .orElseThrow(() -> new IllegalStateException("No strategy found for type: " + entry.getKey()))
                ));

        // Crear mapa por nombre de estrategia
        strategyNameMap = strategies.stream()
                .collect(Collectors.toMap(
                        DocumentProcessingStrategy::getStrategyName,
                        Function.identity(),
                        (existing, replacement) -> {
                            log.warn("Duplicate strategy name found: {}. Using strategy with higher priority.",
                                    existing.getStrategyName());
                            return existing.getPriority() <= replacement.getPriority() ? existing : replacement;
                        }
                ));

        log.info("Initialized {} strategies for {} document types",
                strategies.size(), strategyMap.size());

        // Log de estrategias disponibles
        strategyMap.forEach((type, strategy) ->
                log.info("Document type {} -> Strategy: {} (Priority: {})",
                        type.getTypeName(), strategy.getStrategyName(), strategy.getPriority()));
    }

    /**
     * Process a document using the appropriate strategy based on the document type.
     *
     * @param document the document to process
     * @return ProcessingResult containing the processing outcome
     * @throws DocumentProcessingException if processing fails
     */
    public ProcessingResult processDocument(Document document) {
        log.info("Processing document: {} (Size: {} bytes)", document.getName(), document.getSize());

        try {
            // Validar documento básico
            validateDocument(document);

            // Determinar tipo de documento
            DocumentType documentType = determineDocumentType(document);
            log.debug("Detected document type: {} for file: {}", documentType, document.getName());

            // Obtener estrategia apropiada
            DocumentProcessingStrategy strategy = getStrategy(documentType);
            log.debug("Using strategy: {} for document: {}", strategy.getStrategyName(), document.getName());

            // Verificar que la estrategia puede procesar el documento
            if (!strategy.canProcess(document)) {
                throw new DocumentProcessingException(
                        String.format("Strategy %s cannot process document %s",
                                strategy.getStrategyName(), document.getName()),
                        document.getName(),
                        strategy.getStrategyName()
                );
            }

            // Procesar documento
            ProcessingResult result = strategy.process(document);

            // Log del resultado
            if (result.isSuccess()) {
                log.info("Successfully processed document: {} using strategy: {} in {} ms",
                        document.getName(), strategy.getStrategyName(), result.getProcessingTimeMs());
            } else {
                log.warn("Failed to process document: {} using strategy: {}. Errors: {}",
                        document.getName(), strategy.getStrategyName(), result.getErrors());
            }

            return result;

        } catch (UnsupportedDocumentTypeException | StrategyNotFoundException e) {
            log.error("Document type not supported: {}", document.getName(), e);
            throw e;
        } catch (DocumentProcessingException e) {
            log.error("Document processing failed: {}", document.getName(), e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error processing document: {}", document.getName(), e);
            throw new DocumentProcessingException(
                    "Unexpected error during document processing: " + e.getMessage(),
                    document.getName(),
                    null,
                    e
            );
        }
    }

    /**
     * Process a document using a specific strategy by name.
     * Useful for testing or when you want to force a specific strategy.
     *
     * @param document the document to process
     * @param strategyName the name of the strategy to use
     * @return ProcessingResult containing the processing outcome
     * @throws DocumentProcessingException if processing fails
     */
    public ProcessingResult processDocumentWithStrategy(Document document, String strategyName) {
        log.info("Processing document: {} with specific strategy: {}", document.getName(), strategyName);

        try {
            validateDocument(document);

            DocumentProcessingStrategy strategy = strategyNameMap.get(strategyName);
            if (strategy == null) {
                throw new StrategyNotFoundException(
                        String.format("Strategy not found: %s. Available strategies: %s",
                                strategyName, getAvailableStrategies()),
                        null
                );
            }

            if (!strategy.canProcess(document)) {
                throw new DocumentProcessingException(
                        String.format("Strategy %s cannot process document %s of type %s",
                                strategyName, document.getName(), document.determineTypeFromExtension()),
                        document.getName(),
                        strategyName
                );
            }

            return strategy.process(document);

        } catch (Exception e) {
            log.error("Error processing document with specific strategy: {}", strategyName, e);
            if (e instanceof DocumentProcessingException) {
                throw e;
            }
            throw new DocumentProcessingException(
                    "Error processing document with strategy " + strategyName + ": " + e.getMessage(),
                    document.getName(),
                    strategyName,
                    e
            );
        }
    }

    /**
     * Get information about all available strategies.
     *
     * @return Map containing strategy information
     */
    public Map<String, Object> getStrategiesInfo() {
        Map<String, Object> info = new HashMap<>();

        // Información general
        info.put("totalStrategies", strategies.size());
        info.put("supportedDocumentTypes", strategyMap.keySet().stream()
                .map(DocumentType::getTypeName)
                .collect(Collectors.toList()));

        // Información detallada de cada estrategia
        List<Map<String, Object>> strategiesDetails = strategies.stream()
                .map(strategy -> {
                    Map<String, Object> details = new HashMap<>();
                    details.put("name", strategy.getStrategyName());
                    details.put("supportedType", strategy.getSupportedType().getTypeName());
                    details.put("supportedExtensions", strategy.getSupportedType().getSupportedExtensions());
                    details.put("priority", strategy.getPriority());
                    details.put("className", strategy.getClass().getSimpleName());
                    return details;
                })
                .sorted(Comparator.comparingInt(details -> (Integer) details.get("priority")))
                .collect(Collectors.toList());

        info.put("strategies", strategiesDetails);

        return info;
    }

    /**
     * Check if a document type is supported.
     *
     * @param documentType the document type to check
     * @return true if supported, false otherwise
     */
    public boolean isDocumentTypeSupported(DocumentType documentType) {
        return strategyMap.containsKey(documentType);
    }

    /**
     * Check if a file extension is supported.
     *
     * @param fileExtension the file extension to check (with or without dot)
     * @return true if supported, false otherwise
     */
    public boolean isFileExtensionSupported(String fileExtension) {
        try {
            DocumentType.fromExtension(fileExtension);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Get the list of all supported file extensions.
     *
     * @return Set of supported extensions
     */
    public Set<String> getSupportedExtensions() {
        return Arrays.stream(DocumentType.values())
                .flatMap(type -> type.getSupportedExtensions().stream())
                .collect(Collectors.toSet());
    }

    /**
     * Get processing statistics across all strategies.
     * This could be extended to track actual usage statistics.
     *
     * @return Map containing processing statistics
     */
    public Map<String, Object> getProcessingStatistics() {
        Map<String, Object> stats = new HashMap<>();

        // Por ahora, estadísticas básicas sobre configuración
        stats.put("configuredStrategies", strategies.size());
        stats.put("supportedTypes", strategyMap.size());
        stats.put("strategyPriorities", strategies.stream()
                .collect(Collectors.toMap(
                        DocumentProcessingStrategy::getStrategyName,
                        DocumentProcessingStrategy::getPriority
                )));

        // Aquí se podrían agregar métricas reales de uso si se implementa un sistema de tracking

        return stats;
    }

    // Métodos privados de ayuda

    private void validateDocument(Document document) {
        if (document == null) {
            throw new IllegalArgumentException("Document cannot be null");
        }

        if (document.getName() == null || document.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Document name cannot be null or empty");
        }

        if (document.getContent() == null || document.getContent().length == 0) {
            throw new IllegalArgumentException("Document content cannot be null or empty");
        }

        // Establecer el tamaño si no está definido
        if (document.getSize() == null) {
            document.setSize((long) document.getContent().length);
        }
    }

    private DocumentType determineDocumentType(Document document) {
        try {
            return DocumentType.fromExtension(document.getFileExtension());
        } catch (IllegalArgumentException e) {
            throw new UnsupportedDocumentTypeException(
                    document.getFileExtension(),
                    document.getName()
            );
        }
    }

    private DocumentProcessingStrategy getStrategy(DocumentType documentType) {
        DocumentProcessingStrategy strategy = strategyMap.get(documentType);
        if (strategy == null) {
            throw new StrategyNotFoundException(documentType, null);
        }
        return strategy;
    }

    private List<String> getAvailableStrategies() {
        return new ArrayList<>(strategyNameMap.keySet());
    }
}