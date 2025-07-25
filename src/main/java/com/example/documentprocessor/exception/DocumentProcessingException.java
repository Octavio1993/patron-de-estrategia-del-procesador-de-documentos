package com.example.documentprocessor.exception;

import lombok.Getter;

/**
 * Excepción lanzada cuando ocurre un error durante el procesamiento de documentos.
 * Es una excepción general para cualquier error relacionado con el procesamiento.
 */
@Getter
public class DocumentProcessingException extends RuntimeException {

    private final String documentName;
    private final String strategyName;
    private final String errorCode;

    public DocumentProcessingException(String message) {
        super(message);
        this.documentName = null;
        this.strategyName = null;
        this.errorCode = "PROCESSING_ERROR";
    }

    public DocumentProcessingException(String message, Throwable cause) {
        super(message, cause);
        this.documentName = null;
        this.strategyName = null;
        this.errorCode = "PROCESSING_ERROR";
    }

    public DocumentProcessingException(String message, String documentName, String strategyName) {
        super(message);
        this.documentName = documentName;
        this.strategyName = strategyName;
        this.errorCode = "PROCESSING_ERROR";
    }

    public DocumentProcessingException(String message, String documentName, String strategyName, String errorCode) {
        super(message);
        this.documentName = documentName;
        this.strategyName = strategyName;
        this.errorCode = errorCode;
    }

    public DocumentProcessingException(String message, String documentName, String strategyName, Throwable cause) {
        super(message, cause);
        this.documentName = documentName;
        this.strategyName = strategyName;
        this.errorCode = "PROCESSING_ERROR";
    }

    public DocumentProcessingException(String message, String documentName, String strategyName, String errorCode, Throwable cause) {
        super(message, cause);
        this.documentName = documentName;
        this.strategyName = strategyName;
        this.errorCode = errorCode;
    }

    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder();

        if (documentName != null) {
            sb.append("Document '").append(documentName).append("': ");
        }

        if (strategyName != null) {
            sb.append("[").append(strategyName).append("] ");
        }

        sb.append(super.getMessage());

        if (errorCode != null && !errorCode.equals("PROCESSING_ERROR")) {
            sb.append(" (Error Code: ").append(errorCode).append(")");
        }

        return sb.toString();
    }
}