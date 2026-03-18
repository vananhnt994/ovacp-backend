package de.bht.app.aianalysis.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

/**
 * Eine vom KI-Modell empfohlene Chart-Konfiguration.
 * Nach der Extraktion wird das Chart sofort generiert und
 * die fertigen Daten in {@code generatedData} gespeichert.
 * Das Frontend muss nur noch rendern - kein weiterer API-Call noetig.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChartSuggestion {

    /** BAR, HISTOGRAM, HEATMAP, PIE */
    private String chartType;

    /** Welche Datei (z.B. "train.csv") */
    private String fileName;

    /** Haupt-Spalte (X-Achse / Kategorie / Bins) */
    private String column;

    /** Optionale zweite Spalte (Wert-Spalte / Y-Achse bei Heatmap) */
    private String valueColumn;

    /** Aggregation: SUM, AVG, COUNT, MIN, MAX */
    private String aggregation;

    /** Anzahl Bins (nur Histogram) */
    private Integer bins;

    /** Top-N (nur Bar/Pie) */
    private Integer topN;

    /** Kurze Beschreibung warum dieses Chart sinnvoll ist */
    private String reason;

    /**
     * Vom chart-data-service vorberechnete Chart-Daten.
     * Wird NICHT von Gemini gesetzt, sondern vom AnalysisOrchestrator
     * nach der Extraktion aufgefuellt.
     * Struktur je nach chartType:
     *   BAR:       { chartType, title, xAxisLabel, yAxisLabel, data: { labels[], values[] } }
     *   HISTOGRAM: { chartType, title, ..., data: { binLabels[], frequencies[] } }
     *   HEATMAP:   { chartType, title, ..., data: { xLabels[], yLabels[], matrix[][] } }
     *   PIE:       { chartType, title, ..., data: { labels[], values[] } }
     */
    private Map<String, Object> generatedData;

    /** Fehlermeldung falls Chart-Generierung fehlschlug */
    private String generationError;

    public ChartSuggestion() {}

    // ── Getter & Setter ──────────────────────────────────────

    public String getChartType() { return chartType; }
    public void setChartType(String chartType) { this.chartType = chartType; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getColumn() { return column; }
    public void setColumn(String column) { this.column = column; }

    public String getValueColumn() { return valueColumn; }
    public void setValueColumn(String valueColumn) { this.valueColumn = valueColumn; }

    public String getAggregation() { return aggregation; }
    public void setAggregation(String aggregation) { this.aggregation = aggregation; }

    public Integer getBins() { return bins; }
    public void setBins(Integer bins) { this.bins = bins; }

    public Integer getTopN() { return topN; }
    public void setTopN(Integer topN) { this.topN = topN; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Map<String, Object> getGeneratedData() { return generatedData; }
    public void setGeneratedData(Map<String, Object> generatedData) { this.generatedData = generatedData; }

    public String getGenerationError() { return generationError; }
    public void setGenerationError(String generationError) { this.generationError = generationError; }

    @Override
    public String toString() {
        return "ChartSuggestion{" +
                "chartType='" + chartType + '\'' +
                ", fileName='" + fileName + '\'' +
                ", column='" + column + '\'' +
                ", valueColumn='" + valueColumn + '\'' +
                ", aggregation='" + aggregation + '\'' +
                ", bins=" + bins +
                ", topN=" + topN +
                ", reason='" + reason + '\'' +
                ", hasData=" + (generatedData != null) +
                '}';
    }
}

