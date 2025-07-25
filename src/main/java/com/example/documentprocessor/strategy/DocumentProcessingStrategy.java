package com.example.documentprocessor.strategy;

import com.example.documentprocessor.model.Document;
import com.example.documentprocessor.model.DocumentType;
import com.example.documentprocessor.model.ProcessingResult;

/**
 * Interfaz de estrategia para procesar diferentes tipos de documentos.
 * Cada implementación gestiona un tipo de documento específico (PDF, Excel, Word, CSV).
 */
public interface DocumentProcessingStrategy {

    /**
     * Procesa el documento dado y devuelve el resultado del procesamiento.
     *
     * @param document el documento a procesar
     * @return ProcessingResult contiene los datos extraídos, los metadatos y el estado del procesamiento
     */
    ProcessingResult process(Document document);

    /**
     * Obtener el tipo de documento que esta estrategia puede gestionar.
     *
     * @return el tipo de documento admitido
     */
    DocumentType getSupportedType();

    /**
     * Obtener el nombre de la estrategia para fines de identificación.
     *
     * @return nombre de la estrategia
     */
    String getStrategyName();

    /**
     * Validar si el documento puede procesarse con esta estrategia.
     *
     * @param document el documento a validar
     * @return true si el documento puede procesarse, false en caso contrario
     */
    default boolean canProcess(Document document) {
        if (document == null || document.getContent() == null || document.getContent().length == 0) {
            return false;
        }

        try {
            DocumentType documentType = DocumentType.fromExtension(document.getFileExtension());
            return getSupportedType().equals(documentType);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Validar el documento antes de procesarlo.
     * Este método puede ser anulado por implementaciones para validaciones específicas.
     *
     * @param document el documento a validar
     * @throws com.example.documentprocessor.exception.DocumentProcessingException si la validación falla
     */
    default void validateDocument(Document document) {
        if (document == null) {
            throw new IllegalArgumentException("El documento no puede ser nulo.");
        }

        if (document.getContent() == null || document.getContent().length == 0) {
            throw new IllegalArgumentException("El contenido del documento no puede ser nulo o vacío");
        }

        if (document.getName() == null || document.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del documento no puede ser nulo ni estar vacío");
        }

        if (!canProcess(document)) {
            throw new IllegalArgumentException(
                    String.format("El tipo de documento '%s' no es compatible con la estrategia '%s'",
                            document.determineTypeFromExtension(), getStrategyName())
            );
        }
    }

    /**
     * Obtener la prioridad de procesamiento. Los números más bajos indican una prioridad más alta.
     * Útil cuando varias estrategias pueden gestionar el mismo tipo.
     *
     * @return valor de prioridad (predeterminado: 100)
     */
    default int getPriority() {
        return 100;
    }
}