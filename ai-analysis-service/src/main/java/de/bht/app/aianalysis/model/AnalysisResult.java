package de.bht.app.aianalysis.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO fuer das Analyse-Ergebnis, das an das Frontend zurueckgegeben wird.
 */
public class AnalysisResult {

    /** Die Antwort des KI-Modells */
    private String answer;

    /** Welche Dateien analysiert wurden */
    private List<String> analyzedFiles;

    /** Welches Modell verwendet wurde */
    private String model;

    /** Verarbeitungsdauer in Millisekunden */
    private long processingTimeMs;

    /** Zeitstempel der Analyse */
    private LocalDateTime timestamp;

    /** Anzahl der an das Modell gesendeten Datenzeilen */
    private long totalRowsSent;

    /** Fehler-Flag */
    private boolean error;

    /** Fehlermeldung (falls error == true) */
    private String errorMessage;

    /** Vom KI-Modell empfohlene Visualisierungen */
    private List<ChartSuggestion> chartSuggestions;

    public AnalysisResult() {
        this.timestamp = LocalDateTime.now();
    }

    public static AnalysisResult success(String answer, List<String> analyzedFiles,
                                         String model, long processingTimeMs,
                                         long totalRowsSent,
                                         List<ChartSuggestion> chartSuggestions) {
        AnalysisResult r = new AnalysisResult();
        r.answer = answer;
        r.analyzedFiles = analyzedFiles;
        r.model = model;
        r.processingTimeMs = processingTimeMs;
        r.totalRowsSent = totalRowsSent;
        r.error = false;
        r.chartSuggestions = chartSuggestions;
        return r;
    }

    public static AnalysisResult failure(String errorMessage) {
        AnalysisResult r = new AnalysisResult();
        r.error = true;
        r.errorMessage = errorMessage;
        return r;
    }

    // ── Getter & Setter ──────────────────────────────────────

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public List<String> getAnalyzedFiles() {
        return analyzedFiles;
    }

    public void setAnalyzedFiles(List<String> analyzedFiles) {
        this.analyzedFiles = analyzedFiles;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public long getProcessingTimeMs() {
        return processingTimeMs;
    }

    public void setProcessingTimeMs(long processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public long getTotalRowsSent() {
        return totalRowsSent;
    }

    public void setTotalRowsSent(long totalRowsSent) {
        this.totalRowsSent = totalRowsSent;
    }

    public boolean isError() {
        return error;
    }

    public void setError(boolean error) {
        this.error = error;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public List<ChartSuggestion> getChartSuggestions() {
        return chartSuggestions;
    }

    public void setChartSuggestions(List<ChartSuggestion> chartSuggestions) {
        this.chartSuggestions = chartSuggestions;
    }
}

