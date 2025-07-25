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
 * Servicio que organiza el procesamiento de documentos mediante el patrón de estrategia.
 * Detecta y gestiona automáticamente todas las estrategias de procesamiento disponibles.
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
        log.info("Inicializando estrategias de procesamiento de documentos...");

        // Crear mapa por tipo de documento, priorizando por priority()
        strategyMap = strategies.stream()
                .collect(Collectors.groupingBy(DocumentProcessingStrategy::getSupportedType))
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .min(Comparator.comparingInt(DocumentProcessingStrategy::getPriority))
                                .orElseThrow(() -> new IllegalStateException("No se encontró ninguna estrategia para el tipo: " + entry.getKey()))
                ));

        // Crear mapa por nombre de estrategia
        strategyNameMap = strategies.stream()
                .collect(Collectors.toMap(
                        DocumentProcessingStrategy::getStrategyName,
                        Function.identity(),
                        (existing, replacement) -> {
                            log.warn("Se encontró un nombre de estrategia duplicado: {}. Se está usando una estrategia con mayor prioridad..",
                                    existing.getStrategyName());
                            return existing.getPriority() <= replacement.getPriority() ? existing : replacement;
                        }
                ));

        log.info("Estrategias {} inicializadas para tipos de documentos {}",
                strategies.size(), strategyMap.size());

        // Log de estrategias disponibles
        strategyMap.forEach((type, strategy) ->
                log.info("Tipo de documento {} -> Estrategia: {} (Prioridad: {})",
                        type.getTypeName(), strategy.getStrategyName(), strategy.getPriority()));
    }

    /**
     * Procesar un documento utilizando la estrategia adecuada según el tipo de documento.
     *
     * @param document el documento a procesar
     * @return ProcessingResult que contiene el resultado del procesamiento
     * @throws DocumentProcessingException si el procesamiento falla
     */
    public ProcessingResult processDocument(Document document) {
        log.info("Procesando documento: {} (Tamaño: {} bytes)", document.getName(), document.getSize());

        try {
            // Validar documento básico
            validateDocument(document);

            // Determinar tipo de documento
            DocumentType documentType = determineDocumentType(document);
            log.debug("Tipo de documento detectado: {} para el archivo: {}", documentType, document.getName());

            // Obtener estrategia apropiada
            DocumentProcessingStrategy strategy = getStrategy(documentType);
            log.debug("Usando la estrategia: {} para el documento: {}", strategy.getStrategyName(), document.getName());

            // Verificar que la estrategia puede procesar el documento
            if (!strategy.canProcess(document)) {
                throw new DocumentProcessingException(
                        String.format("La estrategia %s no puede procesar el documento %s",
                                strategy.getStrategyName(), document.getName()),
                        document.getName(),
                        strategy.getStrategyName()
                );
            }

            // Procesar documento
            ProcessingResult result = strategy.process(document);

            // Log del resultado
            if (result.isSuccess()) {
                log.info("Documento procesado exitosamente: {} usando la estrategia: {} en {} ms",
                        document.getName(), strategy.getStrategyName(), result.getProcessingTimeMs());
            } else {
                log.warn("No se pudo procesar el documento: {} con la estrategia: {}. Errores: {}",
                        document.getName(), strategy.getStrategyName(), result.getErrors());
            }

            return result;

        } catch (UnsupportedDocumentTypeException | StrategyNotFoundException e) {
            log.error("Tipo de documento no admitido: {}", document.getName(), e);
            throw e;
        } catch (DocumentProcessingException e) {
            log.error("Error en el procesamiento del documento: {}", document.getName(), e);
            throw e;
        } catch (Exception e) {
            log.error("Error inesperado al procesar el documento: {}", document.getName(), e);
            throw new DocumentProcessingException(
                    "Error inesperado durante el procesamiento del documento: " + e.getMessage(),
                    document.getName(),
                    null,
                    e
            );
        }
    }

    /**
     * Procesar un documento usando una estrategia específica por nombre.
     * Útil para realizar pruebas o cuando se desea forzar una estrategia específica.
     *
     * @param document el documento a procesar
     * @param strategyName el nombre de la estrategia a usar
     * @return ProcessingResult contiene el resultado del procesamiento
     * @throws DocumentProcessingException si el procesamiento falla
     */
    public ProcessingResult processDocumentWithStrategy(Document document, String strategyName) {
        log.info("Procesando documento: {} con estrategia específica: {}", document.getName(), strategyName);

        try {
            validateDocument(document);

            DocumentProcessingStrategy strategy = strategyNameMap.get(strategyName);
            if (strategy == null) {
                throw new StrategyNotFoundException(
                        String.format("Estrategia no encontrada: %s. Estrategias disponibles: %s",
                                strategyName, getAvailableStrategies()),
                        null
                );
            }

            if (!strategy.canProcess(document)) {
                throw new DocumentProcessingException(
                        String.format("La estrategia %s no puede procesar el documento %s del tipo %s",
                                strategyName, document.getName(), document.determineTypeFromExtension()),
                        document.getName(),
                        strategyName
                );
            }

            return strategy.process(document);

        } catch (Exception e) {
            log.error("Error al procesar un documento con una estrategia específica: {}", strategyName, e);
            if (e instanceof DocumentProcessingException) {
                throw e;
            }
            throw new DocumentProcessingException(
                    "Error al procesar documento con estrategia " + strategyName + ": " + e.getMessage(),
                    document.getName(),
                    strategyName,
                    e
            );
        }
    }

    /**
     * Obtener información sobre todas las estrategias disponibles.
     *
     * @return Mapa con información de la estrategia
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
     * Comprueba si se admite un tipo de documento.
     *
     * @param documentType el tipo de documento a comprobar
     * @return true si se admite, false en caso contrario
     */
    public boolean isDocumentTypeSupported(DocumentType documentType) {
        return strategyMap.containsKey(documentType);
    }

    /**
     * Comprueba si se admite una extensión de archivo.
     *
     * @param fileExtension la extensión del archivo a comprobar (con o sin punto)
     * @return true si se admite, false en caso contrario
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
     * Obtener la lista de todas las extensiones de archivo compatibles.
     *
     * @return Conjunto de extensiones compatibles
     */
    public Set<String> getSupportedExtensions() {
        return Arrays.stream(DocumentType.values())
                .flatMap(type -> type.getSupportedExtensions().stream())
                .collect(Collectors.toSet());
    }

    /**
     * Obtener estadísticas de procesamiento de todas las estrategias.
     * Esto podría ampliarse para rastrear las estadísticas de uso real.
     *
     * @return Mapa que contiene las estadísticas de procesamiento.
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
            throw new IllegalArgumentException("El documento no puede ser nulo.");
        }

        if (document.getName() == null || document.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del documento no puede ser nulo o estar vacío");
        }

        if (document.getContent() == null || document.getContent().length == 0) {
            throw new IllegalArgumentException("El contenido del documento no puede estar vacío o ser nulo");
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