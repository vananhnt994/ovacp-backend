package de.bht.app.aianalysis.service;

import de.bht.app.aianalysis.prompt.PromptBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holt CSV-Zusammenfassungen vom File-Management-Service via Eureka.
 * Ruft den /summary Endpoint auf, der serverseitig Statistiken berechnet
 * und nur eine Stichprobe zurueckgibt (kein 120 MB JSON mehr).
 */
@Service
public class DataPreparationService {

    private static final Logger log = LoggerFactory.getLogger(DataPreparationService.class);

    private final WebClient fileServiceClient;
    private final int defaultSampleRows;

    public DataPreparationService(WebClient.Builder loadBalancedWebClientBuilder,
                                  @Value("${prompt.default-sample-rows:50}") int defaultSampleRows) {
        this.fileServiceClient = loadBalancedWebClientBuilder
                .baseUrl("http://file-management")
                .build();
        this.defaultSampleRows = defaultSampleRows;
    }

    /**
     * Laedt Zusammenfassungen (Statistiken + Stichprobe) fuer alle Dateien.
     * Ruft GET /api/files/{filename}/summary?sampleRows=N auf.
     */
    public Map<String, PromptBuilder.CsvData> fetchDatasets(List<String> filenames) {
        Map<String, PromptBuilder.CsvData> result = new LinkedHashMap<>();

        for (String filename : filenames) {
            try {
                log.info("Lade Summary vom File-Service: {}", filename);

                Map<String, Object> response = fileServiceClient.get()
                        .uri("/api/files/{filename}/summary?sampleRows={sample}",
                                filename, defaultSampleRows)
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                        .block();

                if (response == null) {
                    log.warn("Keine Daten erhalten fuer: {}", filename);
                    continue;
                }

                @SuppressWarnings("unchecked")
                List<String> headers = (List<String>) response.get("headers");
                int totalRows = response.get("totalRows") instanceof Number n ? n.intValue() : 0;

                @SuppressWarnings("unchecked")
                List<List<String>> sampleRows = (List<List<String>>) response.get("sampleRows");

                @SuppressWarnings("unchecked")
                Map<String, Object> columnStats = (Map<String, Object>) response.get("columnStats");

                result.put(filename, new PromptBuilder.CsvData(
                        headers != null ? headers : List.of(),
                        sampleRows != null ? sampleRows : List.of(),
                        totalRows,
                        columnStats
                ));

                log.info("Summary {} geladen: {} Spalten, {} Gesamtzeilen, {} Stichproben",
                        filename,
                        headers != null ? headers.size() : 0,
                        totalRows,
                        sampleRows != null ? sampleRows.size() : 0);

            } catch (WebClientResponseException.NotFound e) {
                log.error("Datei nicht gefunden: {}", filename);
                throw new IllegalArgumentException("Datei nicht gefunden: " + filename);
            } catch (Exception e) {
                log.error("Fehler beim Laden von {}: {}", filename, e.getMessage());
                throw new RuntimeException(
                        "Fehler beim Laden der Datei " + filename + ": " + e.getMessage(), e);
            }
        }
        return result;
    }

    public List<String> listAvailableFiles() {
        return fileServiceClient.get()
                .uri("/api/files")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<String>>() {})
                .block();
    }
}
