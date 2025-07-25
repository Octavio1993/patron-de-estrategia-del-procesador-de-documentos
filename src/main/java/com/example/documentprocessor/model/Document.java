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

    @NotBlank(message = "Document name cannot be blank")
    private String name;

    @NotBlank(message = "Document type cannot be blank")
    private String type; // PDF, EXCEL, WORD, CSV

    @NotNull(message = "Document content cannot be null")
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