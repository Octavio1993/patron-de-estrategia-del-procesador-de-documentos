package com.example.documentprocessor.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Set;

@Getter
@RequiredArgsConstructor
public enum DocumentType {

    PDF("PDF", Set.of("pdf"), "application/pdf"),
    EXCEL("EXCEL", Set.of("xlsx", "xls"), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    WORD("WORD", Set.of("docx", "doc"), "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    CSV("CSV", Set.of("csv"), "text/csv");

    private final String typeName;
    private final Set<String> supportedExtensions;
    private final String mimeType;

    public static DocumentType fromExtension(String extension) {
        if (extension == null || extension.trim().isEmpty()) {
            throw new IllegalArgumentException("La extensión no puede ser nula o vacía");
        }

        String cleanExtension = extension.toLowerCase().trim();
        if (cleanExtension.startsWith(".")) {
            cleanExtension = cleanExtension.substring(1);
        }

        final String finalExtension = cleanExtension;
        return Arrays.stream(values())
                .filter(type -> type.getSupportedExtensions().contains(finalExtension))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Extensión de archivo no compatible: " + extension));
    }

    public static DocumentType fromTypeName(String typeName) {
        if (typeName == null || typeName.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del tipo no puede ser nulo ni estar vacío");
        }

        return Arrays.stream(values())
                .filter(type -> type.getTypeName().equalsIgnoreCase(typeName.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Tipo de documento desconocido: " + typeName));
    }

    public boolean supportsExtension(String extension) {
        if (extension == null) return false;
        String cleanExtension = extension.toLowerCase().trim();
        if (cleanExtension.startsWith(".")) {
            cleanExtension = cleanExtension.substring(1);
        }
        return supportedExtensions.contains(cleanExtension);
    }
}