package com.example.documentprocessor.strategy.impl;

import com.example.documentprocessor.exception.DocumentProcessingException;
import com.example.documentprocessor.model.Document;
import com.example.documentprocessor.model.DocumentMetadata;
import com.example.documentprocessor.model.DocumentType;
import com.example.documentprocessor.model.ProcessingResult;
import com.example.documentprocessor.strategy.DocumentProcessingStrategy;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ExcelProcessingStrategy implements DocumentProcessingStrategy {

    @Override
    public ProcessingResult process(Document document) {
        long startTime = System.currentTimeMillis();

        ProcessingResult.ProcessingResultBuilder resultBuilder = ProcessingResult.builder()
                .documentName(document.getName())
                .processingStrategy(getStrategyName())
                .success(false);

        Workbook workbook = null;
        try {
            validateDocument(document);

            // Determinar el tipo de Excel y crear workbook apropiado
            workbook = createWorkbook(document);

            // Procesar todas las hojas
            List<Map<String, Object>> allSheetsData = new ArrayList<>();
            Map<String, Object> workbookStats = new HashMap<>();

            int totalRows = 0;
            int totalCells = 0;
            List<Map<String, Object>> sheetsInfo = new ArrayList<>();

            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                Map<String, Object> sheetData = processSheet(sheet, i);
                allSheetsData.add(sheetData);

                // Acumular estadísticas
                Map<String, Object> sheetStats = (Map<String, Object>) sheetData.get("statistics");
                totalRows += (Integer) sheetStats.get("dataRows");
                totalCells += (Integer) sheetStats.get("totalCells");

                // Info de la hoja para metadata
                Map<String, Object> sheetInfo = new HashMap<>();
                sheetInfo.put("name", sheet.getSheetName());
                sheetInfo.put("index", i);
                sheetInfo.put("rowCount", sheetStats.get("dataRows"));
                sheetInfo.put("columnCount", sheetStats.get("columnCount"));
                sheetInfo.put("hasData", sheetStats.get("hasData"));
                sheetsInfo.add(sheetInfo);
            }

            // Crear metadata del workbook
            DocumentMetadata metadata = createMetadata(document, workbook, sheetsInfo);

            // Estadísticas globales
            workbookStats.put("totalSheets", workbook.getNumberOfSheets());
            workbookStats.put("totalRows", totalRows);
            workbookStats.put("totalCells", totalCells);
            workbookStats.put("sheetsInfo", sheetsInfo);
            workbookStats.put("hasFormulas", hasFormulas(workbook));
            workbookStats.put("hasMergedCells", hasMergedCells(workbook));

            long processingTime = System.currentTimeMillis() - startTime;

            return resultBuilder
                    .success(true)
                    .message(String.format("Archivo de Excel procesado correctamente con %d hojas y %d filas en total",
                            workbook.getNumberOfSheets(), totalRows))
                    .metadata(metadata)
                    .extractedData(allSheetsData)
                    .processingStats(workbookStats)
                    .processingTimeMs(processingTime)
                    .build();

        } catch (Exception e) {
            log.error("Error al procesar el documento de Excel: {}", document.getName(), e);
            long processingTime = System.currentTimeMillis() - startTime;

            return resultBuilder
                    .success(false)
                    .message("No se pudo procesar el documento de Excel")
                    .errors(List.of("error de procesamiento: " + e.getMessage()))
                    .processingTimeMs(processingTime)
                    .build();
        } finally {
            if (workbook != null) {
                try {
                    workbook.close();
                } catch (IOException e) {
                    log.warn("Error al cerrar el libro de Excel: {}", document.getName(), e);
                }
            }
        }
    }

    private Workbook createWorkbook(Document document) throws IOException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(document.getContent());

        // Intentar crear XLSX primero, luego XLS
        try {
            return new XSSFWorkbook(inputStream);
        } catch (Exception e) {
            // Si falla, intentar con formato XLS
            inputStream.reset();
            return new HSSFWorkbook(inputStream);
        }
    }

    private Map<String, Object> processSheet(Sheet sheet, int sheetIndex) {
        Map<String, Object> sheetData = new HashMap<>();

        sheetData.put("sheetName", sheet.getSheetName());
        sheetData.put("sheetIndex", sheetIndex);

        // Determinar rango de datos
        int firstRow = sheet.getFirstRowNum();
        int lastRow = sheet.getLastRowNum();

        if (firstRow == -1 || lastRow == -1) {
            // Hoja vacía
            sheetData.put("hasData", false);
            sheetData.put("data", new ArrayList<>());
            sheetData.put("headers", new ArrayList<>());
            sheetData.put("statistics", createEmptySheetStats());
            return sheetData;
        }

        // Detectar número de columnas
        int maxColumns = 0;
        for (int i = firstRow; i <= lastRow; i++) {
            Row row = sheet.getRow(i);
            if (row != null) {
                maxColumns = Math.max(maxColumns, row.getLastCellNum());
            }
        }

        // Detectar si tiene headers
        boolean hasHeaders = detectHeaders(sheet, firstRow, maxColumns);

        // Extraer headers
        List<String> headers = extractHeaders(sheet, firstRow, maxColumns, hasHeaders);

        // Extraer datos
        int dataStartRow = hasHeaders ? firstRow + 1 : firstRow;
        List<Map<String, Object>> rowsData = extractRowsData(sheet, dataStartRow, lastRow, headers);

        // Generar estadísticas de la hoja
        Map<String, Object> sheetStats = generateSheetStatistics(sheet, rowsData, headers, hasHeaders);

        sheetData.put("hasData", true);
        sheetData.put("headers", headers);
        sheetData.put("data", rowsData);
        sheetData.put("statistics", sheetStats);
        sheetData.put("hasHeaders", hasHeaders);

        return sheetData;
    }

    private boolean detectHeaders(Sheet sheet, int firstRow, int maxColumns) {
        Row firstRowObj = sheet.getRow(firstRow);
        Row secondRowObj = sheet.getRow(firstRow + 1);

        if (firstRowObj == null || secondRowObj == null || maxColumns == 0) {
            return false;
        }

        int textCellsInFirst = 0;
        int numberCellsInSecond = 0;
        int totalCells = 0;

        for (int col = 0; col < maxColumns; col++) {
            Cell firstCell = firstRowObj.getCell(col);
            Cell secondCell = secondRowObj.getCell(col);

            if (firstCell != null) {
                totalCells++;
                if (firstCell.getCellType() == CellType.STRING) {
                    textCellsInFirst++;
                }
            }

            if (secondCell != null && isNumericCell(secondCell)) {
                numberCellsInSecond++;
            }
        }

        // Heurística: si la primera fila tiene mayoría de texto y la segunda mayoría de números
        return totalCells > 0 &&
                (double) textCellsInFirst / totalCells > 0.6 &&
                numberCellsInSecond > textCellsInFirst * 0.5;
    }

    private boolean isNumericCell(Cell cell) {
        return cell.getCellType() == CellType.NUMERIC &&
                !DateUtil.isCellDateFormatted(cell);
    }

    private List<String> extractHeaders(Sheet sheet, int firstRow, int maxColumns, boolean hasHeaders) {
        List<String> headers = new ArrayList<>();

        if (hasHeaders) {
            Row headerRow = sheet.getRow(firstRow);
            if (headerRow != null) {
                for (int col = 0; col < maxColumns; col++) {
                    Cell cell = headerRow.getCell(col);
                    String headerValue = getCellValueAsString(cell);
                    headers.add(headerValue.isEmpty() ? "Column_" + (col + 1) : headerValue);
                }
            }
        } else {
            // Generar headers por defecto
            for (int col = 0; col < maxColumns; col++) {
                headers.add("Column_" + (col + 1));
            }
        }

        return headers;
    }

    private List<Map<String, Object>> extractRowsData(Sheet sheet, int startRow, int endRow, List<String> headers) {
        List<Map<String, Object>> rowsData = new ArrayList<>();

        for (int rowNum = startRow; rowNum <= endRow; rowNum++) {
            Row row = sheet.getRow(rowNum);
            if (row == null) continue;

            Map<String, Object> rowData = new LinkedHashMap<>();
            boolean hasData = false;

            for (int col = 0; col < headers.size(); col++) {
                Cell cell = row.getCell(col);
                Object cellValue = getCellValue(cell);
                rowData.put(headers.get(col), cellValue);

                if (cellValue != null) {
                    hasData = true;
                }
            }

            // Solo agregar filas que tienen al menos una celda con datos
            if (hasData) {
                rowsData.add(rowData);
            }
        }

        return rowsData;
    }

    private Object getCellValue(Cell cell) {
        if (cell == null) {
            return null;
        }

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getDateCellValue().toInstant()
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime();
                } else {
                    double numValue = cell.getNumericCellValue();
                    // Si es un número entero, devolver como Long
                    if (numValue == Math.floor(numValue) && Double.isFinite(numValue)) {
                        yield (long) numValue;
                    } else {
                        yield numValue;
                    }
                }
            }
            case BOOLEAN -> cell.getBooleanCellValue();
            case FORMULA -> {
                try {
                    // Intentar obtener el valor calculado
                    yield switch (cell.getCachedFormulaResultType()) {
                        case STRING -> cell.getStringCellValue();
                        case NUMERIC -> {
                            if (DateUtil.isCellDateFormatted(cell)) {
                                yield cell.getDateCellValue().toInstant()
                                        .atZone(ZoneId.systemDefault())
                                        .toLocalDateTime();
                            } else {
                                double numValue = cell.getNumericCellValue();
                                if (numValue == Math.floor(numValue) && Double.isFinite(numValue)) {
                                    yield (long) numValue;
                                } else {
                                    yield numValue;
                                }
                            }
                        }
                        case BOOLEAN -> cell.getBooleanCellValue();
                        default -> "[Formula: " + cell.getCellFormula() + "]";
                    };
                } catch (Exception e) {
                    yield "[Formula: " + cell.getCellFormula() + "]";
                }
            }
            case BLANK -> null;
            default -> cell.toString();
        };
    }

    private String getCellValueAsString(Cell cell) {
        Object value = getCellValue(cell);
        return value != null ? value.toString() : "";
    }

    private Map<String, Object> generateSheetStatistics(Sheet sheet, List<Map<String, Object>> rowsData,
                                                        List<String> headers, boolean hasHeaders) {
        Map<String, Object> stats = new HashMap<>();

        stats.put("sheetName", sheet.getSheetName());
        stats.put("hasData", !rowsData.isEmpty());
        stats.put("hasHeaders", hasHeaders);
        stats.put("dataRows", rowsData.size());
        stats.put("columnCount", headers.size());

        // Contar celdas totales y con datos
        int totalCells = rowsData.size() * headers.size();
        long cellsWithData = rowsData.stream()
                .flatMap(row -> row.values().stream())
                .filter(Objects::nonNull)
                .count();

        stats.put("totalCells", totalCells);
        stats.put("cellsWithData", (int) cellsWithData);
        stats.put("emptyCells", totalCells - (int) cellsWithData);
        stats.put("dataCompleteness", totalCells > 0 ? (double) cellsWithData / totalCells : 0.0);

        // Estadísticas por columna
        Map<String, Map<String, Object>> columnStats = new HashMap<>();
        for (String header : headers) {
            Map<String, Object> colStats = new HashMap<>();

            List<Object> columnValues = rowsData.stream()
                    .map(row -> row.get(header))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            colStats.put("nonNullCount", columnValues.size());
            colStats.put("nullCount", rowsData.size() - columnValues.size());

            if (!columnValues.isEmpty()) {
                // Contar tipos de datos
                long numberCount = columnValues.stream().mapToLong(v -> v instanceof Number ? 1 : 0).sum();
                long dateCount = columnValues.stream().mapToLong(v -> v instanceof LocalDateTime ? 1 : 0).sum();
                long booleanCount = columnValues.stream().mapToLong(v -> v instanceof Boolean ? 1 : 0).sum();
                long stringCount = columnValues.size() - numberCount - dateCount - booleanCount;

                colStats.put("numberCount", numberCount);
                colStats.put("dateCount", dateCount);
                colStats.put("booleanCount", booleanCount);
                colStats.put("stringCount", stringCount);
                colStats.put("uniqueValues", columnValues.stream().distinct().count());

                // Estadísticas para columnas numéricas
                if (numberCount > 0) {
                    List<Double> numbers = columnValues.stream()
                            .filter(v -> v instanceof Number)
                            .map(v -> ((Number) v).doubleValue())
                            .collect(Collectors.toList());

                    if (!numbers.isEmpty()) {
                        colStats.put("min", numbers.stream().mapToDouble(Double::doubleValue).min().orElse(0));
                        colStats.put("max", numbers.stream().mapToDouble(Double::doubleValue).max().orElse(0));
                        colStats.put("average", numbers.stream().mapToDouble(Double::doubleValue).average().orElse(0));
                        colStats.put("sum", numbers.stream().mapToDouble(Double::doubleValue).sum());
                    }
                }
            }

            columnStats.put(header, colStats);
        }

        stats.put("columnStatistics", columnStats);

        // Información de celdas combinadas
        List<CellRangeAddress> mergedRegions = sheet.getMergedRegions();
        stats.put("mergedCellsCount", mergedRegions.size());

        return stats;
    }

    private Map<String, Object> createEmptySheetStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("hasData", false);
        stats.put("dataRows", 0);
        stats.put("columnCount", 0);
        stats.put("totalCells", 0);
        stats.put("cellsWithData", 0);
        stats.put("emptyCells", 0);
        stats.put("dataCompleteness", 0.0);
        stats.put("columnStatistics", new HashMap<>());
        stats.put("mergedCellsCount", 0);
        return stats;
    }

    private boolean hasFormulas(Workbook workbook) {
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            for (Row row : sheet) {
                for (Cell cell : row) {
                    if (cell.getCellType() == CellType.FORMULA) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasMergedCells(Workbook workbook) {
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            if (!sheet.getMergedRegions().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private DocumentMetadata createMetadata(Document document, Workbook workbook, List<Map<String, Object>> sheetsInfo) {
        return DocumentMetadata.builder()
                .fileName(document.getName())
                .fileType(DocumentType.EXCEL.getTypeName())
                .fileSizeBytes(document.getSize())
                .sheetCount(workbook.getNumberOfSheets())
                .rowCount(sheetsInfo.stream().mapToInt(sheet -> (Integer) sheet.get("rowCount")).sum())
                .columnCount(sheetsInfo.stream().mapToInt(sheet -> (Integer) sheet.get("columnCount")).max().orElse(0))
                .lastModified(LocalDateTime.now())
                .customProperties(Map.of(
                        "sheetsInfo", sheetsInfo,
                        "hasFormulas", hasFormulas(workbook),
                        "hasMergedCells", hasMergedCells(workbook)
                ))
                .build();
    }

    @Override
    public DocumentType getSupportedType() {
        return DocumentType.EXCEL;
    }

    @Override
    public String getStrategyName() {
        return "EXCEL_PROCESSING_STRATEGY";
    }

    @Override
    public int getPriority() {
        return 15; // Prioridad alta para Excel
    }
}