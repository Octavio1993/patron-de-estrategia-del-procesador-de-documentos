package com.example.documentprocessor.strategy.impl;

import com.example.documentprocessor.exception.DocumentProcessingException;
import com.example.documentprocessor.model.Document;
import com.example.documentprocessor.model.DocumentMetadata;
import com.example.documentprocessor.model.DocumentType;
import com.example.documentprocessor.model.ProcessingResult;
import com.example.documentprocessor.strategy.DocumentProcessingStrategy;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
public class PdfProcessingStrategy implements DocumentProcessingStrategy {

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

        PDDocument pdDocument = null;
        try {
            validateDocument(document);

            // Cargar el documento PDF con password vacío por defecto
            pdDocument = PDDocument.load(new ByteArrayInputStream(document.getContent()), "");

            // Si llegamos aquí, el PDF se cargó correctamente
            // Extraer texto
            String extractedText = extractText(pdDocument);

            // Crear metadata
            DocumentMetadata metadata = createMetadata(document, pdDocument);

            // Generar estadísticas
            Map<String, Object> stats = generateTextStatistics(extractedText, pdDocument.getNumberOfPages());

            long processingTime = System.currentTimeMillis() - startTime;

            return resultBuilder
                    .success(true)
                    .message(String.format("PDF procesado exitosamente con %d páginas",
                            pdDocument.getNumberOfPages()))
                    .metadata(metadata)
                    .extractedText(extractedText)
                    .processingStats(stats)
                    .processingTimeMs(processingTime)
                    .build();

        } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException e) {
            log.error("No se pueden procesar los PDF protegidos con contraseña: {}", document.getName(), e);
            return resultBuilder
                    .success(false)
                    .message("El PDF requiere contraseña para su procesamiento")
                    .errors(List.of("Documento protegido con contraseña"))
                    .processingTimeMs(System.currentTimeMillis() - startTime)
                    .build();

        } catch (IOException e) {
            log.error("Error al leer el documento PDF: {}", document.getName(), e);
            return resultBuilder
                    .success(false)
                    .message("No se pudo leer el documento PDF")
                    .errors(List.of("Error de E/S al leer un PDF: " + e.getMessage()))
                    .processingTimeMs(System.currentTimeMillis() - startTime)
                    .build();

        } catch (Exception e) {
            log.error("Error inesperado al procesar un documento PDF: {}", document.getName(), e);
            return resultBuilder
                    .success(false)
                    .message("Error inesperado durante el procesamiento de PDF")
                    .errors(List.of("error de procesamiento: " + e.getMessage()))
                    .processingTimeMs(System.currentTimeMillis() - startTime)
                    .build();

        } finally {
            if (pdDocument != null) {
                try {
                    pdDocument.close();
                } catch (IOException e) {
                    log.warn("Error al cerrar el documento PDF: {}", document.getName(), e);
                }
            }
        }
    }

    private String extractText(PDDocument document) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();

        // Configurar el stripper para mejor extracción
        stripper.setSortByPosition(true);
        stripper.setLineSeparator("\n");
        stripper.setWordSeparator(" ");
        stripper.setArticleEnd("\n\n");
        stripper.setPageEnd("\n\n--- PAGE BREAK ---\n\n");

        // Extraer texto de todas las páginas
        return stripper.getText(document);
    }

    private DocumentMetadata createMetadata(Document document, PDDocument pdDocument) {
        DocumentMetadata.DocumentMetadataBuilder metadataBuilder = DocumentMetadata.builder()
                .fileName(document.getName())
                .fileType(DocumentType.PDF.getTypeName())
                .fileSizeBytes(document.getSize())
                .pageCount(pdDocument.getNumberOfPages())
                .isEncrypted(pdDocument.isEncrypted())
                .pdfVersion(String.valueOf(pdDocument.getVersion()));

        // Extraer información del documento
        PDDocumentInformation docInfo = pdDocument.getDocumentInformation();
        if (docInfo != null) {
            metadataBuilder
                    .title(docInfo.getTitle())
                    .author(docInfo.getAuthor());

            // Convertir fechas
            if (docInfo.getCreationDate() != null) {
                metadataBuilder.creationDate(
                        docInfo.getCreationDate().toInstant()
                                .atZone(ZoneId.systemDefault())
                                .toLocalDateTime()
                );
            }

            if (docInfo.getModificationDate() != null) {
                metadataBuilder.lastModified(
                        docInfo.getModificationDate().toInstant()
                                .atZone(ZoneId.systemDefault())
                                .toLocalDateTime()
                );
            }

            // Propiedades personalizadas
            Map<String, Object> customProps = new HashMap<>();
            if (docInfo.getSubject() != null) {
                customProps.put("subject", docInfo.getSubject());
            }
            if (docInfo.getKeywords() != null) {
                customProps.put("keywords", docInfo.getKeywords());
            }
            if (docInfo.getCreator() != null) {
                customProps.put("creator", docInfo.getCreator());
            }
            if (docInfo.getProducer() != null) {
                customProps.put("producer", docInfo.getProducer());
            }
            if (!customProps.isEmpty()) {
                metadataBuilder.customProperties(customProps);
            }
        }

        return metadataBuilder.build();
    }

    private Map<String, Object> generateTextStatistics(String text, int pageCount) {
        Map<String, Object> stats = new HashMap<>();

        if (text == null || text.trim().isEmpty()) {
            stats.put("textExtracted", false);
            stats.put("totalPages", pageCount);
            stats.put("characterCount", 0);
            stats.put("wordCount", 0);
            stats.put("sentenceCount", 0);
            stats.put("paragraphCount", 0);
            return stats;
        }

        stats.put("textExtracted", true);
        stats.put("totalPages", pageCount);

        // Estadísticas básicas
        int characterCount = text.length();
        int characterCountNoSpaces = text.replaceAll("\\s", "").length();

        List<String> words = WORD_PATTERN.matcher(text)
                .results()
                .map(matchResult -> matchResult.group().toLowerCase())
                .collect(Collectors.toList());

        int wordCount = words.size();
        int sentenceCount = SENTENCE_PATTERN.split(text).length;
        int paragraphCount = PARAGRAPH_PATTERN.split(text).length;

        stats.put("characterCount", characterCount);
        stats.put("characterCountNoSpaces", characterCountNoSpaces);
        stats.put("wordCount", wordCount);
        stats.put("sentenceCount", sentenceCount);
        stats.put("paragraphCount", paragraphCount);
        stats.put("averageWordsPerPage", pageCount > 0 ? (double) wordCount / pageCount : 0);
        stats.put("averageCharactersPerPage", pageCount > 0 ? (double) characterCount / pageCount : 0);

        // Estadísticas de palabras
        if (!words.isEmpty()) {
            Map<String, Long> wordFrequency = words.stream()
                    .filter(word -> word.length() > 2) // Filtrar palabras muy cortas
                    .collect(Collectors.groupingBy(
                            word -> word,
                            Collectors.counting()
                    ));

            int uniqueWords = wordFrequency.size();
            stats.put("uniqueWordCount", uniqueWords);
            stats.put("averageWordLength", words.stream()
                    .mapToInt(String::length)
                    .average()
                    .orElse(0.0));

            // Top 10 palabras más frecuentes
            List<Map<String, Object>> topWords = wordFrequency.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(10)
                    .map(entry -> Map.<String, Object>of(
                            "word", entry.getKey(),
                            "frequency", entry.getValue()
                    ))
                    .collect(Collectors.toList());

            stats.put("topWords", topWords);
        }

        // Distribución de longitudes de palabras
        if (!words.isEmpty()) {
            Map<Integer, Long> lengthDistribution = words.stream()
                    .collect(Collectors.groupingBy(
                            String::length,
                            Collectors.counting()
                    ));
            stats.put("wordLengthDistribution", lengthDistribution);
        }

        // Detectar idioma aproximado (simple heurística)
        String detectedLanguage = detectLanguage(words);
        stats.put("detectedLanguage", detectedLanguage);

        return stats;
    }

    private String detectLanguage(List<String> words) {
        if (words.isEmpty()) {
            return "unknown";
        }

        // Palabras comunes en diferentes idiomas
        Set<String> englishWords = Set.of("the", "and", "for", "are", "but", "not", "you", "all", "can", "had", "her", "was", "one", "our", "out", "day", "get", "has", "him", "his", "how", "its", "may", "new", "now", "old", "see", "two", "who", "boy", "did", "man", "men", "put", "say", "she", "too", "use");
        Set<String> spanishWords = Set.of("que", "de", "no", "a", "la", "el", "es", "y", "en", "lo", "un", "se", "le", "da", "su", "por", "son", "con", "para", "al", "una", "ser", "te", "ha", "o", "me", "ya", "todo", "mi", "pero", "sus", "muy", "este", "del", "más", "sin", "puede", "estar", "como", "hacer", "dos", "bien", "aquí", "tiempo", "también", "hasta", "vida", "tanto", "casa", "vez");

        long englishCount = words.stream().mapToLong(word -> englishWords.contains(word) ? 1 : 0).sum();
        long spanishCount = words.stream().mapToLong(word -> spanishWords.contains(word) ? 1 : 0).sum();

        if (englishCount > spanishCount) {
            return "english";
        } else if (spanishCount > englishCount) {
            return "spanish";
        } else {
            return "unknown";
        }
    }

    @Override
    public DocumentType getSupportedType() {
        return DocumentType.PDF;
    }

    @Override
    public String getStrategyName() {
        return "PDF_PROCESSING_STRATEGY";
    }

    @Override
    public int getPriority() {
        return 20; // Prioridad media para PDF
    }
}