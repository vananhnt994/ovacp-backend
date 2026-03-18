package de.bht.app.aianalysis.controller;

import de.bht.app.aianalysis.model.AnalysisRequest;
import de.bht.app.aianalysis.model.AnalysisResult;
import de.bht.app.aianalysis.service.AnalysisJobStore;
import de.bht.app.aianalysis.service.AnalysisOrchestrator;
import de.bht.app.aianalysis.service.DataPreparationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * REST-Controller fuer KI-gestuetzte Datenanalyse.
 *
 * Endpoints:
 *   POST /api/analysis              - Analyse asynchron starten (gibt jobId zurueck)
 *   GET  /api/analysis/result/{id}  - Ergebnis abholen (Polling)
 *   GET  /api/analysis/files        - Verfuegbare Dateien auflisten
 *   GET  /api/analysis/health       - Health-Check
 */
@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {
    private static final Logger log = LoggerFactory.getLogger(AnalysisController.class);
    private final AnalysisOrchestrator orchestrator;
    private final DataPreparationService dataPreparationService;
    private final AnalysisJobStore jobStore;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public AnalysisController(AnalysisOrchestrator orchestrator,
                              DataPreparationService dataPreparationService,
                              AnalysisJobStore jobStore) {
        this.orchestrator = orchestrator;
        this.dataPreparationService = dataPreparationService;
        this.jobStore = jobStore;
    }

    /**
     * Startet eine KI-Analyse ASYNCHRON.
     * Gibt sofort eine jobId zurueck, damit das Gateway nicht in ein Timeout laeuft.
     *
     * Response: { "jobId": "uuid" }
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> analyze(@RequestBody AnalysisRequest request) {
        String jobId = UUID.randomUUID().toString();
        log.info("=== ANALYSE-ANFRAGE EMPFANGEN (jobId={}) ===", jobId);
        log.info("Dateien: {}", request.getFilenames());
        log.info("Frage: '{}'", request.getQuestion());
        log.info("Modell: {}", request.getModel());
        log.info("MaxRows: {}", request.getMaxRows());

        jobStore.markProcessing(jobId);

        // Analyse im Hintergrund starten
        executor.submit(() -> {
            try {
                AnalysisResult result = orchestrator.analyze(request);
                if (result.isError()) {
                    log.error("=== ANALYSE FEHLGESCHLAGEN (jobId={}): {} ===", jobId, result.getErrorMessage());
                    jobStore.fail(jobId, result);
                } else {
                    log.info("=== ANALYSE ERFOLGREICH (jobId={}, {} ms) ===", jobId, result.getProcessingTimeMs());
                    jobStore.complete(jobId, result);
                }
            } catch (Exception e) {
                log.error("=== ANALYSE EXCEPTION (jobId={}): {} ===", jobId, e.getMessage(), e);
                jobStore.fail(jobId, AnalysisResult.failure("Analyse fehlgeschlagen: " + e.getMessage()));
            }
        });

        // Sofortige Antwort mit jobId (kein Timeout!)
        return ResponseEntity.accepted().body(Map.of("jobId", jobId));
    }

    /**
     * Polling-Endpoint: Ergebnis einer Analyse abholen.
     *
     * Moegliche Antworten:
     *   200 + AnalysisResult  → Analyse fertig (Ergebnis oder Fehler)
     *   202 + { status: PROCESSING } → Analyse laeuft noch
     *   404 → Job-ID unbekannt
     */
    @GetMapping("/result/{jobId}")
    public ResponseEntity<?> getResult(@PathVariable String jobId) {
        AnalysisJobStore.JobEntry entry = jobStore.get(jobId);
        if (entry == null) {
            return ResponseEntity.notFound().build();
        }
        if (entry.status() == AnalysisJobStore.JobStatus.PROCESSING) {
            return ResponseEntity.accepted().body(Map.of("status", "PROCESSING", "jobId", jobId));
        }
        // DONE oder FAILED → Ergebnis zurueckgeben und aus Store entfernen
        jobStore.remove(jobId);
        if (entry.status() == AnalysisJobStore.JobStatus.FAILED) {
            return ResponseEntity.badRequest().body(entry.result());
        }
        return ResponseEntity.ok(entry.result());
    }

    /**
     * Listet alle im File-Service verfuegbaren CSV-Dateien auf,
     * die fuer eine Analyse verwendet werden koennen.
     */
    @GetMapping("/files")
    public ResponseEntity<?> listAvailableFiles() {
        try {
            List<String> files = dataPreparationService.listAvailableFiles();
            return ResponseEntity.ok(Map.of("availableFiles", files));
        } catch (Exception e) {
            log.error("Fehler beim Abrufen der Dateiliste: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "File-Service nicht erreichbar: " + e.getMessage()));
        }
    }

    /**
     * Einfacher Health-Check.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "ai-analysis-service"
        ));
    }
}
