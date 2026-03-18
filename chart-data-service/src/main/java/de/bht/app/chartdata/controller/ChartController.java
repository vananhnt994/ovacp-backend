package de.bht.app.chartdata.controller;

import de.bht.app.chartdata.model.*;
import de.bht.app.chartdata.service.ChartTransformationService;
import de.bht.app.chartdata.service.FileDataFetchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST-Controller fuer Chart-Daten.
 *
 * Haupt-Endpoints (vom Frontend ueber AI-Suggestions genutzt):
 *   POST   /api/charts/generate       - Einzelnes Chart aus ChartSuggestion generieren
 *   POST   /api/charts/batch          - Mehrere Charts auf einmal generieren
 *
 * Meta-Endpoints (optional, fuer manuelle Konfiguration):
 *   GET    /api/charts/types           - Verfuegbare Chart-Typen
 *   GET    /api/charts/columns?file=.. - Spalten-Info einer Datei
 */
@RestController
@RequestMapping("/api/charts")
public class ChartController {

    private static final Logger log = LoggerFactory.getLogger(ChartController.class);

    private final FileDataFetchService fileDataFetchService;
    private final ChartTransformationService transformationService;

    public ChartController(FileDataFetchService fileDataFetchService,
                           ChartTransformationService transformationService) {
        this.fileDataFetchService = fileDataFetchService;
        this.transformationService = transformationService;
    }

    // ════════════════════════════════════════════════════════════════
    //  META-ENDPOINTS
    // ════════════════════════════════════════════════════════════════

    /**
     * Gibt die verfuegbaren Chart-Typen zurueck.
     * Das Frontend kann damit das Dropdown-Menue befuellen.
     */
    @GetMapping("/types")
    public ResponseEntity<List<Map<String, String>>> getChartTypes() {
        return ResponseEntity.ok(List.of(
                Map.of("id", "BAR", "name", "Balkendiagramm",
                        "description", "Vergleicht Kategorien anhand aggregierter Werte"),
                Map.of("id", "HISTOGRAM", "name", "Histogramm",
                        "description", "Zeigt die Verteilung einer numerischen Spalte"),
                Map.of("id", "HEATMAP", "name", "Heatmap",
                        "description", "Zeigt Zusammenhaenge zwischen zwei kategorialen Spalten"),
                Map.of("id", "PIE", "name", "Tortendiagramm",
                        "description", "Zeigt Anteile von Kategorien am Gesamtwert")
        ));
    }

    /**
     * Analysiert die Spalten einer Datei (Typ, Unique-Values, etc.).
     * Optional fuer manuelle Chart-Konfiguration im Frontend.
     */
    @GetMapping("/columns")
    public ResponseEntity<?> getColumns(@RequestParam String file) {
        try {
            CsvDataset dataset = fileDataFetchService.fetchCsvData(file);
            Map<String, Object> info = transformationService.analyzeColumns(dataset);
            return ResponseEntity.ok(info);
        } catch (Exception e) {
            log.error("Fehler bei Spalten-Analyse fuer {}: {}", file, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  HAUPT-ENDPOINTS (vom Frontend mit AI-Suggestions genutzt)
    // ════════════════════════════════════════════════════════════════

    /**
     * Generiert ein einzelnes Chart.
     * Das Frontend leitet eine ChartSuggestion vom AI-Analysis-Service
     * direkt hierher weiter (1:1 kompatibles JSON-Format).
     *
     * Beispiel-Request (= ChartSuggestion aus AI-Antwort):
     * {
     *   "chartType": "BAR",
     *   "fileName": "train.csv",
     *   "column": "DayOfWeek",
     *   "valueColumn": "Sales",
     *   "aggregation": "AVG",
     *   "topN": 7,
     *   "reason": "..." ← wird ignoriert
     * }
     */
    @PostMapping("/generate")
    public ResponseEntity<?> generateChart(@RequestBody ChartRequest request) {
        log.info("Chart-Anfrage: Typ={}, Datei={}, Spalte={}", request.getChartType(),
                request.getFileName(), request.getColumn());

        try {
            validateRequest(request);
            CsvDataset dataset = fileDataFetchService.fetchCsvData(request.getFileName());
            Object chart = buildChart(dataset, request);
            log.info("Chart erfolgreich generiert: {}", request.getChartType());
            return ResponseEntity.ok(chart);

        } catch (IllegalArgumentException e) {
            log.warn("Validierungsfehler: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Chart-Generierung fehlgeschlagen: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Chart konnte nicht erstellt werden: " + e.getMessage()));
        }
    }

    /**
     * Generiert MEHRERE Charts auf einmal.
     * Das Frontend sendet alle chartSuggestions aus der AI-Analyse-Antwort
     * in einem Request und bekommt alle Charts zurueck.
     *
     * Request:  [ {chartSuggestion1}, {chartSuggestion2}, ... ]
     * Response: [ {chartResponse1},   {chartResponse2},   ... ]
     *
     * Falls ein einzelnes Chart fehlschlaegt, wird ein Fehler-Objekt
     * an der entsprechenden Stelle zurueckgegeben (kein Abbruch).
     */
    @PostMapping("/batch")
    public ResponseEntity<List<Object>> generateBatch(@RequestBody List<ChartRequest> requests) {
        log.info("Batch-Anfrage: {} Chart(s)", requests.size());

        List<Object> results = new ArrayList<>();

        for (int i = 0; i < requests.size(); i++) {
            ChartRequest request = requests.get(i);
            try {
                validateRequest(request);
                CsvDataset dataset = fileDataFetchService.fetchCsvData(request.getFileName());
                Object chart = buildChart(dataset, request);
                results.add(chart);
                log.info("  Chart {}/{} OK: {}", i + 1, requests.size(), request.getChartType());
            } catch (Exception e) {
                log.warn("  Chart {}/{} FEHLER: {}", i + 1, requests.size(), e.getMessage());
                Map<String, Object> errorEntry = new LinkedHashMap<>();
                errorEntry.put("error", true);
                errorEntry.put("chartType", request.getChartType() != null ? request.getChartType().name() : null);
                errorEntry.put("message", e.getMessage());
                results.add(errorEntry);
            }
        }

        log.info("Batch abgeschlossen: {}/{} erfolgreich",
                results.stream().filter(r -> !(r instanceof Map)).count(), requests.size());
        return ResponseEntity.ok(results);
    }

    // ════════════════════════════════════════════════════════════════
    //  INTERNE HILFSMETHODEN
    // ════════════════════════════════════════════════════════════════

    /**
     * Baut das Chart anhand des Typs im Request.
     */
    private Object buildChart(CsvDataset dataset, ChartRequest request) {
        return switch (request.getChartType()) {
            case BAR -> transformationService.buildBarChart(
                    dataset, request.getColumn(), request.getValueColumn(),
                    request.getAggregation(), request.getTopN());
            case HISTOGRAM -> transformationService.buildHistogram(
                    dataset, request.getColumn(), request.getBins());
            case HEATMAP -> {
                if (request.getValueColumn() == null || request.getValueColumn().isBlank()) {
                    throw new IllegalArgumentException(
                            "Heatmap benoetigt 'valueColumn' als zweite Achse (Y-Achse).");
                }
                yield transformationService.buildHeatmap(
                        dataset, request.getColumn(), request.getValueColumn(),
                        null, request.getAggregation(), request.getTopN());
            }
            case PIE -> transformationService.buildPieChart(
                    dataset, request.getColumn(), request.getValueColumn(),
                    request.getAggregation(), request.getTopN());
        };
    }

    private void validateRequest(ChartRequest request) {
        if (request.getChartType() == null) {
            throw new IllegalArgumentException("'chartType' ist erforderlich (BAR, HISTOGRAM, HEATMAP, PIE).");
        }
        if (request.getFileName() == null || request.getFileName().isBlank()) {
            throw new IllegalArgumentException("'fileName' ist erforderlich.");
        }
        if (request.getColumn() == null || request.getColumn().isBlank()) {
            throw new IllegalArgumentException("'column' ist erforderlich.");
        }
    }
}

