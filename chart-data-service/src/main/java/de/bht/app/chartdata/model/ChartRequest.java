package de.bht.app.chartdata.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Anfrage-DTO fuer Chart-Erstellung.
 * Kompatibel mit ChartSuggestion aus dem AI-Analysis-Service:
 * Das Frontend leitet die Suggestions direkt hierher weiter.
 * Unbekannte Felder (z.B. "reason") werden ignoriert.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChartRequest {

    /** Gewuenschter Chart-Typ */
    private ChartType chartType;

    /** CSV-Dateiname (z.B. "train.csv") */
    private String fileName;

    /** Spalte fuer die X-Achse / Kategorien / Bins */
    private String column;

    /** Optionale zweite Spalte (fuer Heatmap: Y-Achse; fuer Bar: Wert-Spalte) */
    private String valueColumn;

    /** Aggregationsfunktion: SUM, AVG, COUNT, MIN, MAX (Default: SUM) */
    private String aggregation;

    /** Anzahl Bins fuer Histogramm (Default: 10) */
    private Integer bins;

    /** Top-N fuer Bar/Pie (Default: alle) */
    private Integer topN;

    // ── Getter & Setter ────────────────────────────────────────────

    public ChartType getChartType() { return chartType; }
    public void setChartType(ChartType chartType) { this.chartType = chartType; }

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
}

