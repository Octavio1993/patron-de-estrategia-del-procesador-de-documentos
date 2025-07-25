package com.example.documentprocessor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentMetadata {

    private String fileName;
    private String fileType;
    private Long fileSizeBytes;
    private Integer pageCount;
    private Integer wordCount;
    private String author;
    private String title;
    private LocalDateTime creationDate;
    private LocalDateTime lastModified;
    private String encoding;

    // Propiedades específicas por tipo de documento
    private Map<String, Object> customProperties;

    // Para Excel
    private Integer sheetCount;
    private Integer rowCount;
    private Integer columnCount;

    // Para CSV
    private String delimiter;
    private Boolean hasHeaders;

    // Para PDF
    private Boolean isEncrypted;
    private String pdfVersion;
}