package com.example.documentprocessor.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Document {

    @NotBlank(message = "El nombre del documento no puede estar en blanco")
    private String name;

    @NotBlank(message = "El tipo de documento no puede estar en blanco")
    private String type; // PDF, EXCEL, WORD, CSV

    @NotNull(message = "El contenido del documento no puede ser nulo")
    private byte[] content;

    private Long size;

    @Builder.Default
    private LocalDateTime uploadTime = LocalDateTime.now();

    // Método helper para obtener la extensión
    public String getFileExtension() {
        if (name == null || !name.contains(".")) {
            return "";
        }
        return name.substring(name.lastIndexOf(".") + 1).toLowerCase();
    }

    // Método helper para determinar el tipo basado en la extensión
    public String determineTypeFromExtension() {
        String extension = getFileExtension();
        return switch (extension) {
            case "pdf" -> "PDF";
            case "xlsx", "xls" -> "EXCEL";
            case "docx", "doc" -> "WORD";
            case "csv" -> "CSV";
            default -> "UNKNOWN";
        };
    }
}