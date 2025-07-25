package com.example.documentprocessor.strategy.impl;

import com.example.documentprocessor.exception.DocumentProcessingException;
import com.example.documentprocessor.model.Document;
import com.example.documentprocessor.model.DocumentMetadata;
import com.example.documentprocessor.model.DocumentType;
import com.example.documentprocessor.model.ProcessingResult;
import com.example.documentprocessor.strategy.DocumentProcessingStrategy;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.hwpf.usermodel.Range;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
public class WordProcessingStrategy implements DocumentProcessingStrategy {

    private static final Pattern WORD_PATTERN = Pattern.compile("\\b\\w+\\b");
    private static final Pattern SENTENCE_PATTERN = Pattern.compile("[.!?]+");
    private static final Pattern PARAGRAPH_PATTERN = Pattern.compile("\\n\\s*\\n");

    @Override
    public ProcessingResult process(Document document) {
        long startTime = System.currentTimeMillis();

        ProcessingResult.ProcessingResultBuilder resultBuilder = ProcessingResult.builder()
                .documentName(document.getName())
                .processingStrategy(getStrategyName())
                .success(false);

        try {
            validateDocument(document);

            String fileExtension = document.getFileExtension().toLowerCase();

            if ("docx".equals(fileExtension)) {
                return processDocx(document, resultBuilder, startTime);
            } else if ("doc".equals(fileExtension)) {
                return processDoc(document, resultBuilder, startTime);
            } else {
                return resultBuilder
                        .success(false)
                        .message("Unsupported Word document format: " + fileExtension)
                        .errors(List.of("Only .doc and .docx formats are supported"))
                        .processingTimeMs(System.currentTimeMillis() - startTime)
                        .build();
            }

        } catch (Exception e) {
            log.error("Error processing Word document: {}", document.getName(), e);
            long processingTime = System.currentTimeMillis() - startTime;

            return resultBuilder
                    .success(false)
                    .message("Failed to process Word document")
                    .errors(List.of("Processing error: " + e.getMessage()))
                    .processingTimeMs(processingTime)
                    .build();
        }
    }

    private ProcessingResult processDocx(Document document, ProcessingResult.ProcessingResultBuilder resultBuilder, long startTime) {
        XWPFDocument docxDocument = null;
        XWPFWordExtractor extractor = null;

        try {
            docxDocument = new XWPFDocument(new ByteArrayInputStream(document.getContent()));
            extractor = new XWPFWordExtractor(docxDocument);

            // Extraer texto completo
            String extractedText = extractor.getText();

            // Procesar contenido estructurado
            Map<String, Object> structuredContent = extractDocxStructuredContent(docxDocument);

            // Crear metadata
            DocumentMetadata metadata = createDocxMetadata(document, docxDocument);

            // Generar estadísticas
            Map<String, Object> stats = generateDocxStatistics(docxDocument, extractedText, structuredContent);

            long processingTime = System.currentTimeMillis() - startTime;

            return resultBuilder
                    .success(true)
                    .message(String.format("Successfully processed DOCX with %d paragraphs and %d tables",
                            docxDocument.getParagraphs().size(), docxDocument.getTables().size()))
                    .metadata(metadata)
                    .extractedText(extractedText)
                    .extractedData(List.of(structuredContent))
                    .processingStats(stats)
                    .processingTimeMs(processingTime)
                    .build();

        } catch (Exception e) {
            log.error("Error processing DOCX document: {}", document.getName(), e);
            return resultBuilder
                    .success(false)
                    .message("Failed to process DOCX document")
                    .errors(List.of("DOCX processing error: " + e.getMessage()))
                    .processingTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        } finally {
            closeResources(extractor, docxDocument);
        }
    }

    private ProcessingResult processDoc(Document document, ProcessingResult.ProcessingResultBuilder resultBuilder, long startTime) {
        HWPFDocument docDocument = null;
        WordExtractor extractor = null;

        try {
            docDocument = new HWPFDocument(new ByteArrayInputStream(document.getContent()));
            extractor = new WordExtractor(docDocument);

            // Extraer texto
            String extractedText = extractor.getText();

            // Procesar contenido (limitado para .doc)
            Map<String, Object> structuredContent = extractDocStructuredContent(docDocument);

            // Crear metadata
            DocumentMetadata metadata = createDocMetadata(document, docDocument);

            // Generar estadísticas
            Map<String, Object> stats = generateDocStatistics(docDocument, extractedText, structuredContent);

            long processingTime = System.currentTimeMillis() - startTime;

            return resultBuilder
                    .success(true)
                    .message("Successfully processed DOC document")
                    .metadata(metadata)
                    .extractedText(extractedText)
                    .extractedData(List.of(structuredContent))
                    .processingStats(stats)
                    .processingTimeMs(processingTime)
                    .build();

        } catch (Exception e) {
            log.error("Error processing DOC document: {}", document.getName(), e);
            return resultBuilder
                    .success(false)
                    .message("Failed to process DOC document")
                    .errors(List.of("DOC processing error: " + e.getMessage()))
                    .processingTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        } finally {
            closeResources(extractor, docDocument);
        }
    }

    private Map<String, Object> extractDocxStructuredContent(XWPFDocument document) {
        Map<String, Object> content = new HashMap<>();

        // Extraer párrafos con estilos
        List<Map<String, Object>> paragraphs = new ArrayList<>();
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            Map<String, Object> paragraphData = new HashMap<>();
            paragraphData.put("text", paragraph.getText());
            paragraphData.put("style", paragraph.getStyle());
            paragraphData.put("alignment", paragraph.getAlignment() != null ? paragraph.getAlignment().toString() : null);

            // Información de runs (formato de texto)
            List<Map<String, Object>> runs = new ArrayList<>();
            for (XWPFRun run : paragraph.getRuns()) {
                Map<String, Object> runData = new HashMap<>();
                runData.put("text", run.getText(0));
                runData.put("isBold", run.isBold());
                runData.put("isItalic", run.isItalic());
                runData.put("fontSize", run.getFontSize());
                runData.put("fontFamily", run.getFontFamily());
                runs.add(runData);
            }
            paragraphData.put("runs", runs);

            if (!paragraph.getText().trim().isEmpty()) {
                paragraphs.add(paragraphData);
            }
        }
        content.put("paragraphs", paragraphs);

        // Extraer tablas
        List<Map<String, Object>> tables = new ArrayList<>();
        for (XWPFTable table : document.getTables()) {
            Map<String, Object> tableData = new HashMap<>();
            List<List<String>> tableRows = new ArrayList<>();

            for (XWPFTableRow row : table.getRows()) {
                List<String> rowData = new ArrayList<>();
                for (XWPFTableCell cell : row.getTableCells()) {
                    rowData.add(cell.getText());
                }
                tableRows.add(rowData);
            }

            tableData.put("rows", tableRows);
            tableData.put("rowCount", table.getRows().size());
            tableData.put("columnCount", tableRows.isEmpty() ? 0 : tableRows.get(0).size());
            tables.add(tableData);
        }
        content.put("tables", tables);

        // Extraer headers y footers
        List<String> headers = new ArrayList<>();
        List<String> footers = new ArrayList<>();

        for (XWPFHeader header : document.getHeaderList()) {
            headers.add(header.getText());
        }
        for (XWPFFooter footer : document.getFooterList()) {
            footers.add(footer.getText());
        }

        content.put("headers", headers);
        content.put("footers", footers);

        return content;
    }

    private Map<String, Object> extractDocStructuredContent(HWPFDocument document) {
        Map<String, Object> content = new HashMap<>();

        // Para .doc, la extracción es más limitada
        Range range = document.getRange();

        // Extraer párrafos básicos
        List<Map<String, Object>> paragraphs = new ArrayList<>();
        for (int i = 0; i < range.numParagraphs(); i++) {
            org.apache.poi.hwpf.usermodel.Paragraph paragraph = range.getParagraph(i);
            String text = paragraph.text();

            if (!text.trim().isEmpty()) {
                Map<String, Object> paragraphData = new HashMap<>();
                paragraphData.put("text", text.trim());
                paragraphData.put("justification", paragraph.getJustification());
                paragraphs.add(paragraphData);
            }
        }
        content.put("paragraphs", paragraphs);

        // Las tablas en .doc son más complejas de extraer, las omitimos por simplicidad
        content.put("tables", new ArrayList<>());
        content.put("headers", new ArrayList<>());
        content.put("footers", new ArrayList<>());

        return content;
    }

    private DocumentMetadata createDocxMetadata(Document document, XWPFDocument docxDocument) {
        DocumentMetadata.DocumentMetadataBuilder metadataBuilder = DocumentMetadata.builder()
                .fileName(document.getName())
                .fileType(DocumentType.WORD.getTypeName())
                .fileSizeBytes(document.getSize());

        // Propiedades del documento
        try {
            org.apache.poi.ooxml.POIXMLProperties properties = docxDocument.getProperties();

            if (properties.getCoreProperties() != null) {
                var coreProps = properties.getCoreProperties();
                metadataBuilder
                        .title(coreProps.getTitle())
                        .author(coreProps.getCreator());

                if (coreProps.getCreated() != null) {
                    metadataBuilder.creationDate(
                            coreProps.getCreated().toInstant()
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDateTime()
                    );
                }

                if (coreProps.getModified() != null) {
                    metadataBuilder.lastModified(
                            coreProps.getModified().toInstant()
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDateTime()
                    );
                }
            }

            // Propiedades personalizadas
            Map<String, Object> customProps = new HashMap<>();

            if (properties.getExtendedProperties() != null) {
                var extProps = properties.getExtendedProperties();
                customProps.put("application", extProps.getApplication());
                customProps.put("company", extProps.getCompany());
                customProps.put("totalTime", extProps.getTotalTime());
                customProps.put("pages", extProps.getPages());
                customProps.put("words", extProps.getWords());
                customProps.put("characters", extProps.getCharacters());
                customProps.put("charactersWithSpaces", extProps.getCharactersWithSpaces());
                customProps.put("paragraphs", extProps.getParagraphs());
                customProps.put("lines", extProps.getLines());
            }

            metadataBuilder.customProperties(customProps);

        } catch (Exception e) {
            log.warn("Error extracting document properties: {}", e.getMessage());
        }

        return metadataBuilder.build();
    }

    private DocumentMetadata createDocMetadata(Document document, HWPFDocument docDocument) {
        DocumentMetadata.DocumentMetadataBuilder metadataBuilder = DocumentMetadata.builder()
                .fileName(document.getName())
                .fileType(DocumentType.WORD.getTypeName())
                .fileSizeBytes(document.getSize());

        try {
            org.apache.poi.hpsf.SummaryInformation summaryInfo = docDocument.getSummaryInformation();
            if (summaryInfo != null) {
                metadataBuilder
                        .title(summaryInfo.getTitle())
                        .author(summaryInfo.getAuthor());

                if (summaryInfo.getCreateDateTime() != null) {
                    metadataBuilder.creationDate(
                            summaryInfo.getCreateDateTime().toInstant()
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDateTime()
                    );
                }

                if (summaryInfo.getLastSaveDateTime() != null) {
                    metadataBuilder.lastModified(
                            summaryInfo.getLastSaveDateTime().toInstant()
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDateTime()
                    );
                }

                // Propiedades adicionales
                Map<String, Object> customProps = new HashMap<>();
                customProps.put("wordCount", summaryInfo.getWordCount());
                customProps.put("charCount", summaryInfo.getCharCount());
                customProps.put("pageCount", summaryInfo.getPageCount());

                metadataBuilder.customProperties(customProps);
            }
        } catch (Exception e) {
            log.warn("Error extracting DOC properties: {}", e.getMessage());
        }

        return metadataBuilder.build();
    }

    private Map<String, Object> generateDocxStatistics(XWPFDocument document, String extractedText,
                                                       Map<String, Object> structuredContent) {
        Map<String, Object> stats = new HashMap<>();

        // Estadísticas básicas del documento
        stats.put("paragraphCount", document.getParagraphs().size());
        stats.put("tableCount", document.getTables().size());
        stats.put("headerCount", document.getHeaderList().size());
        stats.put("footerCount", document.getFooterList().size());

        // Estadísticas de texto
        addTextStatistics(stats, extractedText);

        // Estadísticas de formato
        addFormattingStatistics(stats, document);

        // Estadísticas de tablas
        addTableStatistics(stats, (List<Map<String, Object>>) structuredContent.get("tables"));

        return stats;
    }

    private Map<String, Object> generateDocStatistics(HWPFDocument document, String extractedText,
                                                      Map<String, Object> structuredContent) {
        Map<String, Object> stats = new HashMap<>();

        // Estadísticas básicas
        Range range = document.getRange();
        stats.put("paragraphCount", range.numParagraphs());
        stats.put("sectionCount", range.numSections());

        // Estadísticas de texto
        addTextStatistics(stats, extractedText);

        return stats;
    }

    private void addTextStatistics(Map<String, Object> stats, String text) {
        if (text == null || text.trim().isEmpty()) {
            stats.put("hasText", false);
            stats.put("characterCount", 0);
            stats.put("wordCount", 0);
            stats.put("sentenceCount", 0);
            return;
        }

        stats.put("hasText", true);
        stats.put("characterCount", text.length());
        stats.put("characterCountNoSpaces", text.replaceAll("\\s", "").length());

        List<String> words = WORD_PATTERN.matcher(text)
                .results()
                .map(matchResult -> matchResult.group().toLowerCase())
                .collect(Collectors.toList());

        stats.put("wordCount", words.size());
        stats.put("sentenceCount", SENTENCE_PATTERN.split(text).length);
        stats.put("paragraphCount", PARAGRAPH_PATTERN.split(text).length);

        if (!words.isEmpty()) {
            stats.put("averageWordLength", words.stream()
                    .mapToInt(String::length)
                    .average()
                    .orElse(0.0));

            // Palabras más frecuentes
            Map<String, Long> wordFrequency = words.stream()
                    .filter(word -> word.length() > 2)
                    .collect(Collectors.groupingBy(
                            word -> word,
                            Collectors.counting()
                    ));

            List<Map<String, Object>> topWords = wordFrequency.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(10)
                    .map(entry -> Map.<String, Object>of(
                            "word", entry.getKey(),
                            "frequency", entry.getValue()
                    ))
                    .collect(Collectors.toList());

            stats.put("topWords", topWords);
            stats.put("uniqueWordCount", wordFrequency.size());
        }
    }

    private void addFormattingStatistics(Map<String, Object> stats, XWPFDocument document) {
        int boldRuns = 0;
        int italicRuns = 0;
        int totalRuns = 0;
        Set<String> fontFamilies = new HashSet<>();
        Set<Integer> fontSizes = new HashSet<>();

        for (XWPFParagraph paragraph : document.getParagraphs()) {
            for (XWPFRun run : paragraph.getRuns()) {
                totalRuns++;
                if (run.isBold()) boldRuns++;
                if (run.isItalic()) italicRuns++;

                if (run.getFontFamily() != null) {
                    fontFamilies.add(run.getFontFamily());
                }
                if (run.getFontSize() > 0) {
                    fontSizes.add(run.getFontSize());
                }
            }
        }

        Map<String, Object> formatting = new HashMap<>();
        formatting.put("totalRuns", totalRuns);
        formatting.put("boldRuns", boldRuns);
        formatting.put("italicRuns", italicRuns);
        formatting.put("fontFamiliesUsed", new ArrayList<>(fontFamilies));
        formatting.put("fontSizesUsed", new ArrayList<>(fontSizes));

        stats.put("formatting", formatting);
    }

    private void addTableStatistics(Map<String, Object> stats, List<Map<String, Object>> tables) {
        if (tables.isEmpty()) {
            stats.put("tableStatistics", Map.of("totalTables", 0));
            return;
        }

        int totalRows = tables.stream()
                .mapToInt(table -> (Integer) table.get("rowCount"))
                .sum();

        int totalColumns = tables.stream()
                .mapToInt(table -> (Integer) table.get("columnCount"))
                .max()
                .orElse(0);

        Map<String, Object> tableStats = new HashMap<>();
        tableStats.put("totalTables", tables.size());
        tableStats.put("totalRows", totalRows);
        tableStats.put("maxColumns", totalColumns);
        tableStats.put("averageRowsPerTable", tables.isEmpty() ? 0 : (double) totalRows / tables.size());

        stats.put("tableStatistics", tableStats);
    }

    private void closeResources(AutoCloseable... resources) {
        for (AutoCloseable resource : resources) {
            if (resource != null) {
                try {
                    resource.close();
                } catch (Exception e) {
                    log.warn("Error closing resource: {}", e.getMessage());
                }
            }
        }
    }

    @Override
    public DocumentType getSupportedType() {
        return DocumentType.WORD;
    }

    @Override
    public String getStrategyName() {
        return "WORD_PROCESSING_STRATEGY";
    }

    @Override
    public int getPriority() {
        return 25; // Prioridad media para Word
    }
}