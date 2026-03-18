package de.bht.app.chartdata.service;

import de.bht.app.chartdata.model.CsvDataset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Holt CSV-Daten vom File-Management-Service ueber Eureka-Discovery.
 * Laedt die ROHE CSV-Datei (nicht JSON) und parst sie lokal.
 * Dadurch funktioniert es auch fuer sehr grosse Dateien (train.csv ~40MB).
 */
@Service
public class FileDataFetchService {

    private static final Logger log = LoggerFactory.getLogger(FileDataFetchService.class);
    private final WebClient webClient;

    public FileDataFetchService(WebClient.Builder loadBalancedWebClientBuilder) {
        this.webClient = loadBalancedWebClientBuilder
                .baseUrl("http://file-management")
                .build();
    }

    /**
     * Laedt die rohe CSV-Datei vom File-Service und parst sie lokal.
     * Nutzt den /download Endpoint (text/csv statt JSON).
     */
    public CsvDataset fetchCsvData(String filename) {
        log.info("Lade rohe CSV vom File-Service: {}", filename);
        try {
            String rawCsv = webClient.get()
                    .uri("/api/files/download/{filename}", filename)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (rawCsv == null || rawCsv.isBlank()) {
                throw new RuntimeException("Keine Daten erhalten fuer: " + filename);
            }

            // Lokal parsen
            CsvDataset dataset = parseCsv(filename, rawCsv);
            log.info("CSV geladen und geparst: {} ({} Spalten, {} Zeilen)",
                    filename, dataset.getHeaders().size(), dataset.getRowCount());
            return dataset;

        } catch (Exception e) {
            log.error("Fehler beim Laden von {}: {}", filename, e.getMessage());
            throw new RuntimeException("Fehler beim Laden der Datei " + filename + ": " + e.getMessage(), e);
        }
    }

    /**
     * Parst einen rohen CSV-String in ein CsvDataset.
     */
    private CsvDataset parseCsv(String filename, String rawCsv) {
        try (BufferedReader reader = new BufferedReader(new StringReader(rawCsv))) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                throw new RuntimeException("CSV hat keine Header-Zeile: " + filename);
            }

            List<String> headers = Arrays.asList(headerLine.split(",", -1));
            List<List<String>> rows = new ArrayList<>();

            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    rows.add(Arrays.asList(line.split(",", -1)));
                }
            }

            CsvDataset dataset = new CsvDataset();
            dataset.setFilename(filename);
            dataset.setHeaders(headers);
            dataset.setRows(rows);
            dataset.setRowCount(rows.size());
            return dataset;

        } catch (Exception e) {
            throw new RuntimeException("Fehler beim Parsen von " + filename + ": " + e.getMessage(), e);
        }
    }

    /**
     * Gibt die Liste aller hochgeladenen Dateinamen zurueck.
     */
    @SuppressWarnings("unchecked")
    public List<String> listFiles() {
        log.info("Liste alle Dateien vom File-Service");
        return webClient.get()
                .uri("/api/files")
                .retrieve()
                .bodyToMono(List.class)
                .block();
    }
}
