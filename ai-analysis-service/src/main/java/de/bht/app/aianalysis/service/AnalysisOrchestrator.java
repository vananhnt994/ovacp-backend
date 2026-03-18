package de.bht.app.aianalysis.service;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.bht.app.aianalysis.model.AnalysisRequest;
import de.bht.app.aianalysis.model.AnalysisResult;
import de.bht.app.aianalysis.model.ChartSuggestion;
import de.bht.app.aianalysis.prompt.PromptBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
/**
 * Orchestriert den vollstaendigen Analyse-Ablauf:
 *   1. CSV-Statistiken vom File-Service laden (kompakt, ~10KB)
 *   2. Prompt aufbauen
 *   3. Gemini-API aufrufen
 *   4. Gemini berechnet Analyse + fertige Chart-Daten aus den Statistiken
 *   5. Chart-Daten aus Antwort extrahieren
 *   6. Ergebnis zurueckgeben (Text + fertige Charts)
 *
 * Kein chart-data-service oder Datei-Download noetig!
 * Gemini IST der Datenanalyst.
 */
@Service
public class AnalysisOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(AnalysisOrchestrator.class);
    private static final String CHARTS_START_MARKER = "===CHARTS===";
    private static final String CHARTS_END_MARKER = "===END_CHARTS===";

    private final DataPreparationService dataPreparationService;
    private final LlmIntegrationService llmIntegrationService;
    private final PromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    public AnalysisOrchestrator(DataPreparationService dataPreparationService,
                                LlmIntegrationService llmIntegrationService,
                                PromptBuilder promptBuilder) {
        this.dataPreparationService = dataPreparationService;
        this.llmIntegrationService = llmIntegrationService;
        this.promptBuilder = promptBuilder;
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    /**
     * Fuehrt eine vollstaendige Analyse durch.
     *
     * @param request die Analyse-Anfrage
     * @return das Analyse-Ergebnis inkl. fertiger Chart-Daten
     */
    public AnalysisResult analyze(AnalysisRequest request) {
        long startTime = System.currentTimeMillis();
        try {
            // 1. Validierung
            validateRequest(request);
            log.info("Starte Analyse: {} Datei(en), Frage: '{}'",
                    request.getFilenames().size(),
                    request.getQuestion().substring(0, Math.min(80, request.getQuestion().length())));
            // 2. Daten vom File-Service laden (nur Statistiken + Stichprobe, kompakt)
            Map<String, PromptBuilder.CsvData> datasets =
                    dataPreparationService.fetchDatasets(request.getFilenames());
            if (datasets.isEmpty()) {
                return AnalysisResult.failure("Keine Daten gefunden fuer die angegebenen Dateien.");
            }
            // 3. Gesamtzeilenanzahl berechnen
            long totalRows = datasets.values().stream()
                    .mapToLong(PromptBuilder.CsvData::totalRows)
                    .sum();
            // 4. Prompt aufbauen
            String prompt = promptBuilder.build(
                    request.getQuestion(),
                    datasets,
                    request.getMaxRows()
            );
            log.info("Prompt erstellt ({} Zeichen, {} Gesamtzeilen)", prompt.length(), totalRows);
            // 5. Gemini aufrufen – berechnet Analyse + Chart-Daten
            String model = request.getModel() != null
                    ? request.getModel()
                    : llmIntegrationService.getDefaultModel();
            String rawAnswer = llmIntegrationService.chat(prompt, model);
            long duration = System.currentTimeMillis() - startTime;
            log.info("Analyse abgeschlossen in {} ms", duration);

            // 6. Fertige Chart-Daten aus Gemini-Antwort extrahieren
            List<String> analyzedFiles = new ArrayList<>(datasets.keySet());
            List<ChartSuggestion> chartSuggestions = extractChartSuggestions(rawAnswer);
            log.info("{} Chart(s) mit fertigen Daten extrahiert", chartSuggestions.size());

            // 7. Text-Antwort bereinigen (Charts-Block entfernen)
            String cleanAnswer = removeChartsBlock(rawAnswer);

            // 8. Ergebnis zurueckgeben
            return AnalysisResult.success(
                    cleanAnswer,
                    analyzedFiles,
                    model,
                    duration,
                    totalRows,
                    chartSuggestions
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

    /**
     * Extrahiert fertige Chart-Daten aus dem ===CHARTS=== ... ===END_CHARTS=== Block.
     * Gemini liefert jetzt direkt berechnete Daten (labels, values, frequencies),
     * nicht nur Konfigurations-Empfehlungen.
     *
     * Jedes Chart-Objekt wird komplett in ChartSuggestion.generatedData gespeichert,
     * sodass das Frontend sie direkt rendern kann.
     */
    @SuppressWarnings("unchecked")
    private List<ChartSuggestion> extractChartSuggestions(String answer) {
        try {
            int startIdx = answer.indexOf(CHARTS_START_MARKER);
            int endIdx = answer.indexOf(CHARTS_END_MARKER);

            if (startIdx < 0 || endIdx < 0 || endIdx <= startIdx) {
                log.debug("Kein Charts-Block in der Antwort gefunden.");
                return List.of();
            }

            String json = answer.substring(startIdx + CHARTS_START_MARKER.length(), endIdx).trim();

            // Markdown Code-Block-Marker entfernen (```json ... ```)
            json = json.replaceAll("^```json\\s*", "")
                       .replaceAll("^```\\s*", "")
                       .replaceAll("\\s*```$", "")
                       .trim();

            if (json.isEmpty() || !json.startsWith("[")) {
                log.warn("Charts-JSON ungueltig: {}",
                        json.substring(0, Math.min(100, json.length())));
                return List.of();
            }

            // Gemini gibt fertige Chart-Objekte mit berechneten data-Feldern aus
            List<Map<String, Object>> rawCharts = objectMapper.readValue(
                    json, new TypeReference<List<Map<String, Object>>>() {});

            List<ChartSuggestion> suggestions = new ArrayList<>();
            for (Map<String, Object> chartMap : rawCharts) {
                String chartType = (String) chartMap.get("chartType");
                String title = (String) chartMap.get("title");
                String reason = (String) chartMap.get("reason");

                if (chartType == null || chartMap.get("data") == null) {
                    log.warn("Chart uebersprungen (kein chartType oder data): {}", chartMap.keySet());
                    continue;
                }

                ChartSuggestion s = new ChartSuggestion();
                s.setChartType(chartType);
                s.setReason(reason != null ? reason : title);
                // Die gesamte Map IST die fertige Chart-Antwort (inkl. data, title, axes)
                s.setGeneratedData(chartMap);

                suggestions.add(s);
                log.info("Chart extrahiert: {} - {}", chartType, title);
            }

            return suggestions;

        } catch (Exception e) {
            log.warn("Chart-Daten konnten nicht geparst werden: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Entfernt den ===CHARTS=== ... ===END_CHARTS=== Block aus der Antwort,
     * damit der User nur den reinen Text sieht.
     */
    private String removeChartsBlock(String answer) {
        int startIdx = answer.indexOf(CHARTS_START_MARKER);
        int endIdx = answer.indexOf(CHARTS_END_MARKER);

        if (startIdx < 0 || endIdx < 0 || endIdx <= startIdx) {
            return answer;
        }

        String before = answer.substring(0, startIdx).trim();
        String after = answer.substring(endIdx + CHARTS_END_MARKER.length()).trim();

        String result = before;
        if (!after.isEmpty()) {
            result += "\n\n" + after;
        }
        return result.trim();
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
