package de.bht.app.filemanagement.service;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import de.bht.app.filemanagement.model.CsvDataset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Service zum Validieren und Parsen von CSV-Dateien.
 * Unterstützt Komma, Semikolon und Tab als Trennzeichen (Auto-Detect).
 */
@Service
public class CsvParserService {

    private static final Logger log = LoggerFactory.getLogger(CsvParserService.class);

    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50 MB
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "text/csv",
            "application/vnd.ms-excel",
            "text/plain",
            "application/octet-stream"
    );

    /**
     * Validiert eine hochgeladene CSV-Datei.
     *
     * @param file die MultipartFile
     * @throws IllegalArgumentException bei Validierungsfehlern
     */
    public void validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Datei ist leer.");
        }
        if (file.getOriginalFilename() == null
                || !file.getOriginalFilename().toLowerCase().endsWith(".csv")) {
            throw new IllegalArgumentException(
                    "Nur CSV-Dateien sind erlaubt. Erhalten: " + file.getOriginalFilename());
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "Datei ist zu groß. Maximum: " + (MAX_FILE_SIZE / 1024 / 1024) + " MB");
        }
        String contentType = file.getContentType();
        if (contentType != null && !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Ungültiger Content-Type: " + contentType);
        }
    }

    /**
     * Parst eine CSV-Datei aus einem MultipartFile.
     *
     * @param file die hochgeladene Datei
     * @return ein CsvDataset mit Headers und Zeilen
     */
    public CsvDataset parse(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown.csv";
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            return doParse(reader, filename);
        }
    }

    /**
     * Parst eine CSV-Datei vom Dateisystem (für spätere Abrufe).
     *
     * @param path Pfad zur Datei
     * @return ein CsvDataset mit Headers und Zeilen
     */
    public CsvDataset parse(Path path) throws IOException {
        String filename = path.getFileName().toString();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return doParse(reader, filename);
        }
    }

    /**
     * Interne Parse-Methode.
     * Erkennt das Trennzeichen automatisch (Komma, Semikolon oder Tab).
     */
    private CsvDataset doParse(Reader reader, String filename) throws IOException {
        // Einlesen des gesamten Inhalts, um das Trennzeichen erkennen zu können
        String content = readAll(reader);
        char separator = detectSeparator(content);

        log.info("Parse {} mit Trennzeichen '{}'", filename, separator == '\t' ? "TAB" : String.valueOf(separator));

        try (CSVReader csvReader = new com.opencsv.CSVReaderBuilder(new StringReader(content))
                .withCSVParser(new com.opencsv.CSVParserBuilder().withSeparator(separator).build())
                .build()) {

            String[] headerLine = csvReader.readNext();
            if (headerLine == null || headerLine.length == 0) {
                throw new IllegalArgumentException("CSV-Datei hat keine Header-Zeile: " + filename);
            }

            List<String> headers = Arrays.asList(headerLine);
            List<List<String>> rows = new ArrayList<>();
            String[] line;
            int lineNumber = 1;

            while ((line = csvReader.readNext()) != null) {
                lineNumber++;
                // Überspringe komplett leere Zeilen
                if (line.length == 1 && line[0].isBlank()) {
                    continue;
                }
                // Warnung bei inkonsistenter Spaltenanzahl
                if (line.length != headers.size()) {
                    log.warn("Zeile {} in {} hat {} Spalten (erwartet: {})",
                            lineNumber, filename, line.length, headers.size());
                }
                rows.add(Arrays.asList(line));
            }

            log.info("{}: {} Spalten, {} Datenzeilen geparst", filename, headers.size(), rows.size());
            return new CsvDataset(filename, headers, rows);

        } catch (CsvValidationException e) {
            throw new IOException("CSV-Validierungsfehler in " + filename + ": " + e.getMessage(), e);
        }
    }

    /** Erkennt automatisch das Trennzeichen anhand der ersten Zeile. */
    private char detectSeparator(String content) {
        // Erste Zeile analysieren
        String firstLine = content.lines().findFirst().orElse("");
        long commas = firstLine.chars().filter(c -> c == ',').count();
        long semicolons = firstLine.chars().filter(c -> c == ';').count();
        long tabs = firstLine.chars().filter(c -> c == '\t').count();

        if (tabs >= commas && tabs >= semicolons && tabs > 0) return '\t';
        if (semicolons >= commas && semicolons > 0) return ';';
        return ',';
    }

    /** Liest den gesamten Reader-Inhalt als String. */
    private String readAll(Reader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buffer = new char[8192];
        int read;
        while ((read = reader.read(buffer)) != -1) {
            sb.append(buffer, 0, read);
        }
        return sb.toString();
    }
}

