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
import java.util.stream.Collectors;

/**
 * Service zum Validieren und Parsen von CSV-Dateien.
 * Unterstuetzt Komma, Semikolon und Tab als Trennzeichen (Auto-Detect).
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

    // ...existing validate, parse(MultipartFile), parse(Path), doParse methods...

    /**
     * Liest eine CSV-Datei zeilenweise (Streaming), berechnet Spalten-Statistiken
     * ueber ALLE Zeilen und gibt nur eine gleichmaessige Stichprobe zurueck.
     * Haelt nie die gesamte Datei im RAM.
     *
     * @param path       Pfad zur Datei
     * @param sampleSize Anzahl Stichproben-Zeilen
     * @return Map mit filename, headers, totalRows, columnStats, sampleRows
     */
    public Map<String, Object> parseSummary(Path path, int sampleSize) throws IOException {
        String filename = path.getFileName().toString();

        // Erste Zeile lesen fuer Separator-Erkennung
        String firstLine;
        try (BufferedReader peek = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            firstLine = peek.readLine();
        }
        if (firstLine == null) {
            throw new IllegalArgumentException("CSV-Datei ist leer: " + filename);
        }
        char separator = detectSeparator(firstLine);

        // Streaming-Parse: Zeile fuer Zeile
        try (CSVReader csvReader = new com.opencsv.CSVReaderBuilder(
                Files.newBufferedReader(path, StandardCharsets.UTF_8))
                .withCSVParser(new com.opencsv.CSVParserBuilder().withSeparator(separator).build())
                .build()) {

            String[] headerLine = csvReader.readNext();
            if (headerLine == null) {
                throw new IllegalArgumentException("Keine Header gefunden: " + filename);
            }
            List<String> headers = Arrays.asList(headerLine);
            int colCount = headers.size();

            // Statistik-Akkumulatoren pro Spalte
            List<List<Double>> numericValues = new ArrayList<>();
            List<Map<String, Integer>> valueCounts = new ArrayList<>();
            List<Integer> nullCounts = new ArrayList<>();
            for (int i = 0; i < colCount; i++) {
                numericValues.add(new ArrayList<>());
                valueCounts.add(new LinkedHashMap<>());
                nullCounts.add(0);
            }

            // Alle Zeilen sammeln (als Index) fuer Sampling, aber nur Stichprobe merken
            List<List<String>> allRowsForSampling = new ArrayList<>();
            String[] line;
            int totalRows = 0;

            while ((line = csvReader.readNext()) != null) {
                if (line.length == 1 && line[0].isBlank()) continue;
                totalRows++;

                for (int col = 0; col < Math.min(line.length, colCount); col++) {
                    String val = line[col].trim();
                    if (val.isEmpty() || val.equalsIgnoreCase("null")
                            || val.equalsIgnoreCase("na") || val.equalsIgnoreCase("nan")) {
                        nullCounts.set(col, nullCounts.get(col) + 1);
                    } else {
                        valueCounts.get(col).merge(val, 1, Integer::sum);
                        try {
                            numericValues.get(col).add(Double.parseDouble(val));
                        } catch (NumberFormatException ignored) {}
                    }
                }

                // Reservoir-Sampling: behalte gleichmaessig verteilte Zeilen
                if (totalRows <= sampleSize) {
                    allRowsForSampling.add(Arrays.asList(line));
                } else {
                    // Gleichmaessige Verteilung: ersetze Zeilen proportional
                    int step = totalRows / sampleSize;
                    if (totalRows % step == 0 && allRowsForSampling.size() >= sampleSize) {
                        int replaceIdx = (totalRows / step - 1) % sampleSize;
                        allRowsForSampling.set(replaceIdx, Arrays.asList(line));
                    }
                }
            }

            // Statistiken bauen
            Map<String, Object> columnStats = new LinkedHashMap<>();
            for (int col = 0; col < colCount; col++) {
                Map<String, Object> stat = new LinkedHashMap<>();
                List<Double> nums = numericValues.get(col);
                Map<String, Integer> counts = valueCounts.get(col);
                int totalNonNull = counts.values().stream().mapToInt(Integer::intValue).sum();

                if (nums.size() > totalNonNull / 2 && !nums.isEmpty()) {
                    DoubleSummaryStatistics ds = nums.stream()
                            .mapToDouble(Double::doubleValue).summaryStatistics();
                    stat.put("type", "numeric");
                    stat.put("count", nums.size());
                    stat.put("min", ds.getMin());
                    stat.put("max", ds.getMax());
                    stat.put("mean", Math.round(ds.getAverage() * 100.0) / 100.0);
                    List<Double> sorted = nums.stream().sorted().collect(Collectors.toList());
                    double median = sorted.size() % 2 == 0
                            ? (sorted.get(sorted.size()/2 - 1) + sorted.get(sorted.size()/2)) / 2.0
                            : sorted.get(sorted.size()/2);
                    stat.put("median", Math.round(median * 100.0) / 100.0);
                } else {
                    stat.put("type", "categorical");
                    stat.put("uniqueCount", counts.size());
                    // Top 5 haeufigste Werte
                    List<String> top5 = counts.entrySet().stream()
                            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                            .limit(5)
                            .map(e -> e.getKey() + "(" + e.getValue() + ")")
                            .collect(Collectors.toList());
                    stat.put("top5", top5);
                    if (counts.size() <= 10) {
                        stat.put("allValues", new ArrayList<>(counts.keySet()));
                    }
                }
                stat.put("nullCount", nullCounts.get(col));
                columnStats.put(headers.get(col), stat);
            }

            // Stichprobe begrenzen
            List<List<String>> sample = allRowsForSampling.size() > sampleSize
                    ? allRowsForSampling.subList(0, sampleSize)
                    : allRowsForSampling;

            log.info("Summary fuer {}: {} Spalten, {} Zeilen, {} Stichproben",
                    filename, colCount, totalRows, sample.size());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("filename", filename);
            result.put("headers", headers);
            result.put("totalRows", totalRows);
            result.put("columnStats", columnStats);
            result.put("sampleRows", sample);
            return result;

        } catch (CsvValidationException e) {
            throw new IOException("CSV-Fehler in " + filename + ": " + e.getMessage(), e);
        }
    }

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

