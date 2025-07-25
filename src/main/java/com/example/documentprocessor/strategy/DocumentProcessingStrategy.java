package com.example.documentprocessor.strategy;

import com.example.documentprocessor.model.Document;
import com.example.documentprocessor.model.DocumentType;
import com.example.documentprocessor.model.ProcessingResult;

/**
 * Strategy interface for processing different types of documents.
 * Each implementation handles a specific document type (PDF, Excel, Word, CSV).
 */
public interface DocumentProcessingStrategy {

    /**
     * Process the given document and return the processing result.
     *
     * @param document the document to process
     * @return ProcessingResult containing extracted data, metadata, and processing status
     */
    ProcessingResult process(Document document);

    /**
     * Get the document type that this strategy can handle.
     *
     * @return the supported DocumentType
     */
    DocumentType getSupportedType();

    /**
     * Get the strategy name for identification purposes.
     *
     * @return strategy name
     */
    String getStrategyName();

    /**
     * Validate if the document can be processed by this strategy.
     *
     * @param document the document to validate
     * @return true if the document can be processed, false otherwise
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
     * Validate the document before processing.
     * This method can be overridden by implementations for specific validations.
     *
     * @param document the document to validate
     * @throws com.example.documentprocessor.exception.DocumentProcessingException if validation fails
     */
    default void validateDocument(Document document) {
        if (document == null) {
            throw new IllegalArgumentException("Document cannot be null");
        }

        if (document.getContent() == null || document.getContent().length == 0) {
            throw new IllegalArgumentException("Document content cannot be null or empty");
        }

        if (document.getName() == null || document.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Document name cannot be null or empty");
        }

        if (!canProcess(document)) {
            throw new IllegalArgumentException(
                    String.format("Document type '%s' is not supported by strategy '%s'",
                            document.determineTypeFromExtension(), getStrategyName())
            );
        }
    }

    /**
     * Get processing priority. Lower numbers indicate higher priority.
     * Useful when multiple strategies might handle the same type.
     *
     * @return priority value (default: 100)
     */
    default int getPriority() {
        return 100;
    }
}