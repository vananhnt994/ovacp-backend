package de.bht.app.aianalysis.service;
import de.bht.app.aianalysis.model.AnalysisRequest;
import de.bht.app.aianalysis.model.AnalysisResult;
import de.bht.app.aianalysis.prompt.PromptBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Map;
/**
 * Orchestriert den vollstaendigen Analyse-Ablauf:
 *   1. CSV-Daten vom File-Service laden
 *   2. Prompt aufbauen
 *   3. Gemini-API aufrufen
 *   4. Ergebnis zurueckgeben
 */
@Service
public class AnalysisOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(AnalysisOrchestrator.class);
    private final DataPreparationService dataPreparationService;
    private final LlmIntegrationService llmIntegrationService;
    private final PromptBuilder promptBuilder;
    public AnalysisOrchestrator(DataPreparationService dataPreparationService,
                                LlmIntegrationService llmIntegrationService,
                                PromptBuilder promptBuilder) {
        this.dataPreparationService = dataPreparationService;
        this.llmIntegrationService = llmIntegrationService;
        this.promptBuilder = promptBuilder;
    }
    /**
     * Fuehrt eine vollstaendige Analyse durch.
     *
     * @param request die Analyse-Anfrage
     * @return das Analyse-Ergebnis
     */
    public AnalysisResult analyze(AnalysisRequest request) {
        long startTime = System.currentTimeMillis();
        try {
            // 1. Validierung
            validateRequest(request);
            log.info("Starte Analyse: {} Datei(en), Frage: '{}'",
                    request.getFilenames().size(),
                    request.getQuestion().substring(0, Math.min(80, request.getQuestion().length())));
            // 2. Daten vom File-Service laden
            Map<String, PromptBuilder.CsvData> datasets =
                    dataPreparationService.fetchDatasets(request.getFilenames());
            if (datasets.isEmpty()) {
                return AnalysisResult.failure("Keine Daten gefunden fuer die angegebenen Dateien.");
            }
            // 3. Gesamtzeilenanzahl berechnen
            long totalRows = datasets.values().stream()
                    .mapToLong(d -> d.totalRows())
                    .sum();
            // 4. Prompt aufbauen
            String prompt = promptBuilder.build(
                    request.getQuestion(),
                    datasets,
                    request.getMaxRows()
            );
            log.info("Prompt erstellt ({} Zeichen, {} Gesamtzeilen)", prompt.length(), totalRows);
            // 5. Gemini aufrufen
            String model = request.getModel() != null
                    ? request.getModel()
                    : llmIntegrationService.getDefaultModel();
            String answer = llmIntegrationService.chat(prompt, model);
            long duration = System.currentTimeMillis() - startTime;
            log.info("Analyse abgeschlossen in {} ms", duration);
            // 6. Ergebnis zurueckgeben
            return AnalysisResult.success(
                    answer,
                    new ArrayList<>(datasets.keySet()),
                    model,
                    duration,
                    totalRows
            );
        } catch (IllegalArgumentException e) {
            log.warn("Validierungsfehler: {}", e.getMessage());
            return AnalysisResult.failure(e.getMessage());
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Analyse fehlgeschlagen nach {} ms: {}", duration, e.getMessage(), e);
            return AnalysisResult.failure("Analyse fehlgeschlagen: " + e.getMessage());
        }
    }
    private void validateRequest(AnalysisRequest request) {
        if (request.getFilenames() == null || request.getFilenames().isEmpty()) {
            throw new IllegalArgumentException("Mindestens ein Dateiname muss angegeben werden.");
        }
        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            throw new IllegalArgumentException("Eine Frage / Analyseaufgabe muss angegeben werden.");
        }
    }
}
