package de.bht.app.filemanagement.controller;
import de.bht.app.filemanagement.model.CsvDataset;
import de.bht.app.filemanagement.model.CsvUploadResponse;
import de.bht.app.filemanagement.service.CsvParserService;
import de.bht.app.filemanagement.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
/**
 * REST-Controller fuer den CSV-Datei-Upload und -Abruf.
 *
 * Endpoints:
 *   POST   /api/files/upload       - Mehrere CSV-Dateien hochladen
 *   GET    /api/files              - Alle gespeicherten Dateien auflisten
 *   GET    /api/files/{filename}   - Geparsten Inhalt einer Datei abrufen
 *   DELETE /api/files/{filename}   - Eine Datei loeschen
 */
@RestController
@RequestMapping("/api/files")
public class FileController {
    private static final Logger log = LoggerFactory.getLogger(FileController.class);
    private final StorageService storageService;
    private final CsvParserService csvParserService;
    public FileController(StorageService storageService, CsvParserService csvParserService) {
        this.storageService = storageService;
        this.csvParserService = csvParserService;
    }
    /**
     * Nimmt eine oder mehrere CSV-Dateien entgegen, validiert, parst und speichert sie.
     *
     * @param files Array von MultipartFile (z.B. test.csv, train.csv, store.csv)
     * @return Liste von CsvUploadResponse - je eine pro Datei
     */
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<List<CsvUploadResponse>> uploadCsvFiles(
            @RequestParam("files") MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return ResponseEntity.badRequest().body(
                    List.of(CsvUploadResponse.error("--", "Keine Dateien empfangen.")));
        }
        List<CsvUploadResponse> results = new ArrayList<>();
        for (MultipartFile file : files) {
            String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
            try {
                // 1. Validieren
                csvParserService.validate(file);
                // 2. Parsen (um Metadaten fuer die Antwort zu bekommen)
                CsvDataset dataset = csvParserService.parse(file);
                // 3. Speichern
                storageService.store(file);
                // 4. Erfolgs-Response bauen
                results.add(CsvUploadResponse.success(
                        filename,
                        dataset.getRowCount(),
                        dataset.getHeaders(),
                        file.getSize()
                ));
                log.info("CSV hochgeladen: {} ({} Zeilen)", filename, dataset.getRowCount());
            } catch (IllegalArgumentException e) {
                log.warn("Validierung fehlgeschlagen fuer {}: {}", filename, e.getMessage());
                results.add(CsvUploadResponse.error(filename, e.getMessage()));
            } catch (Exception e) {
                log.error("Fehler beim Verarbeiten von {}: {}", filename, e.getMessage(), e);
                results.add(CsvUploadResponse.error(filename,
                        "Interner Fehler: " + e.getMessage()));
            }
        }
        // Wenn mindestens eine Datei erfolgreich war -> 200, sonst 400
        boolean anySuccess = results.stream().anyMatch(r -> "OK".equals(r.getStatus()));
        return anySuccess
                ? ResponseEntity.ok(results)
                : ResponseEntity.badRequest().body(results);
    }
    /**
     * Gibt eine Liste aller gespeicherten CSV-Dateinamen zurueck.
     */
    @GetMapping
    public ResponseEntity<List<String>> listFiles() {
        try {
            List<String> files = storageService.listAll();
            return ResponseEntity.ok(files);
        } catch (Exception e) {
            log.error("Fehler beim Auflisten der Dateien: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    /**
     * Gibt eine kompakte Zusammenfassung einer CSV-Datei zurueck:
     * Spalten-Statistiken (ueber ALLE Zeilen) + eine Stichprobe.
     * Ideal fuer den AI-Analysis-Service bei grossen Dateien.
     *
     * @param filename   Dateiname (z.B. "train.csv")
     * @param sampleRows Anzahl Stichproben-Zeilen (default: 50)
     * @return Statistiken + Stichprobe (immer klein, egal wie gross die Datei)
     */
    @GetMapping("/{filename:.+}/summary")
    public ResponseEntity<?> getFileSummary(
            @PathVariable String filename,
            @RequestParam(defaultValue = "50") int sampleRows) {
        try {
            log.info("Summary angefordert fuer {} (sampleRows={})", filename, sampleRows);
            Path path = storageService.load(filename);
            Map<String, Object> summary = csvParserService.parseSummary(path, sampleRows);
            return ResponseEntity.ok(summary);
        } catch (java.nio.file.NoSuchFileException e) {
            return ResponseEntity.status(404).body(
                    Map.of("error", "Datei nicht gefunden: " + filename));
        } catch (Exception e) {
            log.error("Fehler beim Erstellen der Summary fuer {}: {}", filename, e.getMessage());
            return ResponseEntity.internalServerError().body(
                    Map.of("error", "Fehler: " + e.getMessage()));
        }
    }

    /**
     * Gibt den geparsten Inhalt einer gespeicherten CSV-Datei zurueck.
     *
     * @param filename Dateiname (z.B. "train.csv")
     * @return CsvDataset mit Headers und Zeilen
     */
    @GetMapping("/{filename:.+}")
    public ResponseEntity<?> getFileContent(@PathVariable String filename) {
        try {
            Path path = storageService.load(filename);
            CsvDataset dataset = csvParserService.parse(path);
            return ResponseEntity.ok(dataset);
        } catch (java.nio.file.NoSuchFileException e) {
            return ResponseEntity.status(404).body(
                    Map.of("error", "Datei nicht gefunden: " + filename));
        } catch (Exception e) {
            log.error("Fehler beim Lesen von {}: {}", filename, e.getMessage());
            return ResponseEntity.internalServerError().body(
                    Map.of("error", "Fehler beim Lesen: " + e.getMessage()));
        }
    }
    /**
     * Loescht eine gespeicherte CSV-Datei.
     *
     * @param filename Dateiname
     */
    @DeleteMapping("/{filename:.+}")
    public ResponseEntity<?> deleteFile(@PathVariable String filename) {
        try {
            boolean deleted = storageService.delete(filename);
            if (deleted) {
                log.info("Datei geloescht: {}", filename);
                return ResponseEntity.ok(Map.of("message", "Datei geloescht: " + filename));
            } else {
                return ResponseEntity.status(404).body(
                        Map.of("error", "Datei nicht gefunden: " + filename));
            }
        } catch (Exception e) {
            log.error("Fehler beim Loeschen von {}: {}", filename, e.getMessage());
            return ResponseEntity.internalServerError().body(
                    Map.of("error", "Fehler beim Loeschen: " + e.getMessage()));
        }
    }
}
