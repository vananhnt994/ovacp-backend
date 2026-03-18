package de.bht.app.aianalysis.service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
/**
 * Integration mit der Google Gemini API.
 * Sendet einen Prompt und empfaengt die Antwort.
 */
@Service
public class LlmIntegrationService {
    private static final Logger log = LoggerFactory.getLogger(LlmIntegrationService.class);
    private static final String GEMINI_BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/";
    private final WebClient geminiWebClient;
    private final String apiKey;
    private final String defaultModel;
    private final Duration timeout;
    private static final int MAX_RETRIES = 3;

    public LlmIntegrationService(
            @Qualifier("geminiWebClient") WebClient geminiWebClient,
            @Value("${gemini.api.key}") String apiKey,
            @Value("${gemini.model:gemini-2.0-flash}") String defaultModel,
            @Value("${gemini.timeout-seconds:120}") int timeoutSeconds) {
        this.geminiWebClient = geminiWebClient;
        this.apiKey = apiKey;
        this.defaultModel = defaultModel;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }
    /**
     * Sendet einen Prompt an Gemini und gibt die Textantwort zurueck.
     * Bei 429 Rate-Limit wird automatisch bis zu 3x mit Backoff wiederholt.
     *
     * @param prompt das fertige Prompt
     * @param model  optionales Modell (null = default)
     * @return die Textantwort von Gemini
     */
    public String chat(String prompt, String model) {
        String useModel = (model != null && !model.isBlank()) ? model : defaultModel;
        String url = GEMINI_BASE_URL + useModel + ":generateContent?key=" + apiKey;
        log.info("Sende Anfrage an Gemini (Modell: {}, Prompt-Laenge: {} Zeichen)", useModel, prompt.length());
        // Gemini Request Body aufbauen
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)
                        ))
                ),
                "generationConfig", Map.of(
                        "temperature", 0.3,
                        "maxOutputTokens", 8192
                )
        );

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> response = geminiWebClient.post()
                        .uri(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(requestBody)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .timeout(timeout)
                        .block();

                return extractTextFromResponse(response);

            } catch (WebClientResponseException e) {
                if (e.getStatusCode().value() == 429 && attempt < MAX_RETRIES) {
                    long waitSeconds = 30L * attempt;
                    log.warn("Rate-Limit (429) erreicht. Warte {} Sekunden vor Retry {}/{}...",
                            waitSeconds, attempt, MAX_RETRIES);
                    try {
                        Thread.sleep(waitSeconds * 1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry unterbrochen", ie);
                    }
                    continue;
                }
                log.error("Gemini API Fehler {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
                throw new RuntimeException("Gemini API Fehler (" + e.getStatusCode() + "): " + e.getResponseBodyAsString());
            } catch (Exception e) {
                log.error("Gemini-Aufruf fehlgeschlagen: {}", e.getMessage());
                throw new RuntimeException("Gemini-Aufruf fehlgeschlagen: " + e.getMessage(), e);
            }
        }
        throw new RuntimeException("Gemini API nach " + MAX_RETRIES + " Versuchen nicht erreichbar.");
    }
    /**
     * Extrahiert den Text aus der Gemini-API-Antwort.
     * Struktur: { candidates: [ { content: { parts: [ { text: "..." } ] } } ] }
     */
    @SuppressWarnings("unchecked")
    private String extractTextFromResponse(Map<String, Object> response) {
        if (response == null) {
            throw new RuntimeException("Leere Antwort von Gemini erhalten.");
        }
        try {
            List<Map<String, Object>> candidates =
                    (List<Map<String, Object>>) response.get("candidates");
            if (candidates == null || candidates.isEmpty()) {
                // Pruefe auf Fehler / Blockierung
                Object promptFeedback = response.get("promptFeedback");
                if (promptFeedback != null) {
                    throw new RuntimeException("Prompt wurde blockiert: " + promptFeedback);
                }
                throw new RuntimeException("Keine Kandidaten in der Gemini-Antwort.");
            }
            Map<String, Object> content =
                    (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> parts =
                    (List<Map<String, Object>>) content.get("parts");
            StringBuilder text = new StringBuilder();
            for (Map<String, Object> part : parts) {
                Object t = part.get("text");
                if (t != null) {
                    text.append(t);
                }
            }
            String result = text.toString().trim();
            log.info("Gemini-Antwort erhalten ({} Zeichen)", result.length());
            return result;
        } catch (ClassCastException | NullPointerException e) {
            log.error("Unerwartetes Antwortformat von Gemini: {}", response);
            throw new RuntimeException("Unerwartetes Antwortformat von Gemini.", e);
        }
    }
    public String getDefaultModel() {
        return defaultModel;
    }
}
