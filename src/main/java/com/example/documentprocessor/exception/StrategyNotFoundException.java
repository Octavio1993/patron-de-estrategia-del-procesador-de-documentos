package com.example.documentprocessor.exception;

import com.example.documentprocessor.model.DocumentType;
import lombok.Getter;

/**
 * Exception thrown when no strategy is found for a given document type.
 */
@Getter
public class StrategyNotFoundException extends DocumentProcessingException {

    private final DocumentType requestedType;

    public StrategyNotFoundException(DocumentType requestedType) {
        super(String.format("No processing strategy found for document type: %s", requestedType.getTypeName()),
                null, null, "STRATEGY_NOT_FOUND");
        this.requestedType = requestedType;
    }

    public StrategyNotFoundException(DocumentType requestedType, String documentName) {
        super(String.format("No processing strategy found for document type: %s", requestedType.getTypeName()),
                documentName, null, "STRATEGY_NOT_FOUND");
        this.requestedType = requestedType;
    }

    public StrategyNotFoundException(String message, DocumentType requestedType) {
        super(message, null, null, "STRATEGY_NOT_FOUND");
        this.requestedType = requestedType;
    }
}