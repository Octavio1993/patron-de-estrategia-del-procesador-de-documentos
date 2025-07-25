package com.example.documentprocessor.exception;

import lombok.Getter;

import java.util.List;
import java.util.ArrayList;

/**
 * Excepción lanzada cuando falla la validación del documento.
 */
@Getter
public class DocumentValidationException extends DocumentProcessingException {

    private final List<String> validationErrors;

    public DocumentValidationException(String message) {
        super(message, null, null, "VALIDATION_ERROR");
        this.validationErrors = new ArrayList<>();
        this.validationErrors.add(message);
    }

    public DocumentValidationException(String message, String documentName) {
        super(message, documentName, null, "VALIDATION_ERROR");
        this.validationErrors = new ArrayList<>();
        this.validationErrors.add(message);
    }

    public DocumentValidationException(List<String> validationErrors) {
        super("La validación del documento falló: " + String.join(", ", validationErrors),
                null, null, "VALIDATION_ERROR");
        this.validationErrors = new ArrayList<>(validationErrors);
    }

    public DocumentValidationException(List<String> validationErrors, String documentName) {
        super("La validación del documento falló: " + String.join(", ", validationErrors),
                documentName, null, "VALIDATION_ERROR");
        this.validationErrors = new ArrayList<>(validationErrors);
    }

    public void addValidationError(String error) {
        this.validationErrors.add(error);
    }
}