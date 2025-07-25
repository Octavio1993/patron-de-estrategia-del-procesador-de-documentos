package com.example.documentprocessor.exception;

import com.example.documentprocessor.model.DocumentType;
import lombok.Getter;

/**
 * Se lanza una excepción cuando no se encuentra ninguna estrategia para un tipo de documento determinado.
 */
@Getter
public class StrategyNotFoundException extends DocumentProcessingException {

    private final DocumentType requestedType;

    public StrategyNotFoundException(DocumentType requestedType) {
        super(String.format("No se encontró ninguna estrategia de procesamiento para el tipo de documento: %s", requestedType.getTypeName()),
                null, null, "STRATEGY_NOT_FOUND");
        this.requestedType = requestedType;
    }

    public StrategyNotFoundException(DocumentType requestedType, String documentName) {
        super(String.format("No se encontró ninguna estrategia de procesamiento para el tipo de documento: %s", requestedType.getTypeName()),
                documentName, null, "STRATEGY_NOT_FOUND");
        this.requestedType = requestedType;
    }

    public StrategyNotFoundException(String message, DocumentType requestedType) {
        super(message, null, null, "STRATEGY_NOT_FOUND");
        this.requestedType = requestedType;
    }
}