package com.example.documentprocessor.strategy.impl;

import com.example.documentprocessor.exception.DocumentProcessingException;
import com.example.documentprocessor.model.Document;
import com.example.documentprocessor.model.DocumentMetadata;
import com.example.documentprocessor.model.DocumentType;
import com.example.documentprocessor.model.ProcessingResult;
import com.example.documentprocessor.strategy.DocumentProcessingStrategy;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CsvProcessingStrategy implements DocumentProcessingStrategy {

    private static final Set<Character> COMMON_DELIMITERS = Set.of(',', ';', '\t', '|');

    @Override
    public ProcessingResult process(Document document) {
        long startTime = System.currentTimeMillis();

        ProcessingResult.ProcessingResultBuilder resultBuilder = ProcessingResult.builder()
                .documentName(document.getName())
                .processingStrategy(getStrategyName())
                .success(false);

        try {
            validateDocument(document);

            String csvContent = new String(document.getContent(), StandardCharsets.UTF_8);
            char delimiter = detectDelimiter(csvContent);

            // Leer CSV
            List<String[]> csvData = readCsvData(csvContent, delimiter);

            if (csvData.isEmpty()) {
                return resultBuilder
                        .success(false)
                        .message("El archivo CSV está vacío")
                        .errors(List.of("No se encontraron datos en el archivo CSV"))
                        .build();
            }

            // Determinar si tiene headers
            boolean hasHeaders = detectHeaders(csvData);
            String[] headers = hasHeaders ? csvData.get(0) : generateDefaultHeaders(csvData.get(0).length);
            List<String[]> dataRows = hasHeaders ? csvData.subList(1, csvData.size()) : csvData;

            // Convertir a Map para facilitar el uso
            List<Map<String, Object>> structuredData = convertToStructuredData(headers, dataRows);

            // Crear metadata
            DocumentMetadata metadata = createMetadata(document, csvData, delimiter, hasHeaders);

            // Estadísticas
            Map<String, Object> stats = generateStatistics(structuredData, headers);

            long processingTime = System.currentTimeMillis() - startTime;

            return resultBuilder
                    .success(true)
                    .message(String.format("CSV procesado exitosamente con %d filas y %d columnas",
                            dataRows.size(), headers.length))
                    .metadata(metadata)
                    .extractedData(structuredData)
                    .processingStats(stats)
                    .processingTimeMs(processingTime)
                    .build();

        } catch (Exception e) {
            log.error("Error al procesar el documento CSV: {}", document.getName(), e);
            long processingTime = System.currentTimeMillis() - startTime;

            return resultBuilder
                    .success(false)
                    .message("No se pudo procesar el documento CSV")
                    .errors(List.of("error de procesamiento: " + e.getMessage()))
                    .processingTimeMs(processingTime)
                    .build();
        }
    }

    private char detectDelimiter(String csvContent) {
        String firstLine = csvContent.lines().findFirst().orElse("");

        return COMMON_DELIMITERS.stream()
                .max(Comparator.comparingLong(delimiter ->
                        firstLine.chars().filter(ch -> ch == delimiter).count()))
                .orElse(',');
    }

    private List<String[]> readCsvData(String csvContent, char delimiter) throws IOException, CsvException {
        CSVParser parser = new CSVParserBuilder()
                .withSeparator(delimiter)
                .withIgnoreQuotations(false)
                .build();

        try (CSVReader reader = new CSVReaderBuilder(new InputStreamReader(
                new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8))))
                .withCSVParser(parser)
                .build()) {

            return reader.readAll();
        }
    }

    private boolean detectHeaders(List<String[]> csvData) {
        if (csvData.size() < 2) return false;

        String[] firstRow = csvData.get(0);
        String[] secondRow = csvData.get(1);

        // Heurística: si la primera fila tiene strings y la segunda números, probablemente hay headers
        for (int i = 0; i < Math.min(firstRow.length, secondRow.length); i++) {
            if (isNumeric(firstRow[i]) && isNumeric(secondRow[i])) {
                return false; // Ambas son números, probablemente no hay headers
            }
        }
        return true;
    }

    private boolean isNumeric(String str) {
        if (str == null || str.trim().isEmpty()) return false;
        try {
            Double.parseDouble(str.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String[] generateDefaultHeaders(int columnCount) {
        String[] headers = new String[columnCount];
        for (int i = 0; i < columnCount; i++) {
            headers[i] = "Column_" + (i + 1);
        }
        return headers;
    }

    private List<Map<String, Object>> convertToStructuredData(String[] headers, List<String[]> dataRows) {
        return dataRows.stream()
                .map(row -> {
                    Map<String, Object> rowMap = new LinkedHashMap<>();
                    for (int i = 0; i < headers.length; i++) {
                        String value = i < row.length ? row[i] : "";
                        rowMap.put(headers[i], convertValue(value));
                    }
                    return rowMap;
                })
                .collect(Collectors.toList());
    }

    private Object convertValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        value = value.trim();

        // Try to convert to number
        if (isNumeric(value)) {
            try {
                if (value.contains(".")) {
                    return Double.parseDouble(value);
                } else {
                    return Long.parseLong(value);
                }
            } catch (NumberFormatException e) {
                // Fall back to string
            }
        }

        // Try to convert to boolean
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.parseBoolean(value);
        }

        return value;
    }

    private DocumentMetadata createMetadata(Document document, List<String[]> csvData,
                                            char delimiter, boolean hasHeaders) {
        return DocumentMetadata.builder()
                .fileName(document.getName())
                .fileType(DocumentType.CSV.getTypeName())
                .fileSizeBytes(document.getSize())
                .rowCount(csvData.size() - (hasHeaders ? 1 : 0))
                .columnCount(csvData.isEmpty() ? 0 : csvData.get(0).length)
                .delimiter(String.valueOf(delimiter))
                .hasHeaders(hasHeaders)
                .encoding("UTF-8")
                .lastModified(LocalDateTime.now())
                .build();
    }

    private Map<String, Object> generateStatistics(List<Map<String, Object>> data, String[] headers) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRows", data.size());
        stats.put("totalColumns", headers.length);
        stats.put("columns", Arrays.asList(headers));

        // Estadísticas por columna
        Map<String, Map<String, Object>> columnStats = new HashMap<>();
        for (String header : headers) {
            Map<String, Object> colStats = new HashMap<>();

            List<Object> values = data.stream()
                    .map(row -> row.get(header))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            colStats.put("nonNullCount", values.size());
            colStats.put("nullCount", data.size() - values.size());

            if (!values.isEmpty()) {
                // Contar tipos de datos
                long numberCount = values.stream().mapToLong(v -> v instanceof Number ? 1 : 0).sum();
                long booleanCount = values.stream().mapToLong(v -> v instanceof Boolean ? 1 : 0).sum();
                long stringCount = values.size() - numberCount - booleanCount;

                colStats.put("numberCount", numberCount);
                colStats.put("booleanCount", booleanCount);
                colStats.put("stringCount", stringCount);

                // Valores únicos
                colStats.put("uniqueValues", values.stream().distinct().count());
            }

            columnStats.put(header, colStats);
        }

        stats.put("columnStatistics", columnStats);
        return stats;
    }

    @Override
    public DocumentType getSupportedType() {
        return DocumentType.CSV;
    }

    @Override
    public String getStrategyName() {
        return "CSV_PROCESSING_STRATEGY";
    }

    @Override
    public int getPriority() {
        return 10; // Alta prioridad para CSV
    }
}