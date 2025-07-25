package com.example.documentprocessor.exception;

import com.example.documentprocessor.model.DocumentType;
import lombok.Getter;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.Arrays;

/**
 * Se lanza una excepción al intentar procesar un tipo de documento no compatible.
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
                "Tipo de documento no compatible '%s'. Los tipos compatibles son: %s",
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