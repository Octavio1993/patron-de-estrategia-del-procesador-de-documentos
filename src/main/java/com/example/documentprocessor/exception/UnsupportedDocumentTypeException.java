package com.example.documentprocessor.exception;

import com.example.documentprocessor.model.DocumentType;
import lombok.Getter;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.Arrays;

/**
 * Exception thrown when attempting to process a document type that is not supported.
 */
@Getter
public class UnsupportedDocumentTypeException extends DocumentProcessingException {

    private final String fileExtension;
    private final Set<String> supportedExtensions;

    public UnsupportedDocumentTypeException(String fileExtension) {
        super(buildMessage(fileExtension, getSupportedExtensions()),
                null, null, "UNSUPPORTED_DOCUMENT_TYPE");
        this.fileExtension = fileExtension;
        this.supportedExtensions = getSupportedExtensions();
    }

    public UnsupportedDocumentTypeException(String fileExtension, String documentName) {
        super(buildMessage(fileExtension, getSupportedExtensions()),
                documentName, null, "UNSUPPORTED_DOCUMENT_TYPE");
        this.fileExtension = fileExtension;
        this.supportedExtensions = getSupportedExtensions();
    }

    public UnsupportedDocumentTypeException(String message, String fileExtension, String documentName) {
        super(message, documentName, null, "UNSUPPORTED_DOCUMENT_TYPE");
        this.fileExtension = fileExtension;
        this.supportedExtensions = getSupportedExtensions();
    }

    private static String buildMessage(String fileExtension, Set<String> supportedExtensions) {
        return String.format(
                "Unsupported document type '%s'. Supported types are: %s",
                fileExtension,
                String.join(", ", supportedExtensions)
        );
    }

    private static Set<String> getSupportedExtensions() {
        return Arrays.stream(DocumentType.values())
                .flatMap(type -> type.getSupportedExtensions().stream())
                .collect(Collectors.toSet());
    }
}