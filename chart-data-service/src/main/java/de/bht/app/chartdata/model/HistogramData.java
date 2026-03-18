package de.bht.app.chartdata.model;
import java.util.List;
/**
 * Daten fuer ein Histogramm.
 * binLabels[i] beschreibt den Bereich, frequencies[i] die Haeufigkeit.
 */
public class HistogramData {
    private List<String> binLabels;
    private List<Long> frequencies;
    public HistogramData() {}
    public HistogramData(List<String> binLabels, List<Long> frequencies) {
        this.binLabels = binLabels;
        this.frequencies = frequencies;
    }
    public List<String> getBinLabels() { return binLabels; }
    public void setBinLabels(List<String> binLabels) { this.binLabels = binLabels; }
    public List<Long> getFrequencies() { return frequencies; }
    public void setFrequencies(List<Long> frequencies) { this.frequencies = frequencies; }
}
