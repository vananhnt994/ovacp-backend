package de.bht.app.chartdata.model;
import java.util.List;
/**
 * Daten fuer ein Tortendiagramm.
 * labels[i] gehoert zu values[i] (Anteilswerte).
 */
public class PieChartData {
    private List<String> labels;
    private List<Double> values;
    public PieChartData() {}
    public PieChartData(List<String> labels, List<Double> values) {
        this.labels = labels;
        this.values = values;
    }
    public List<String> getLabels() { return labels; }
    public void setLabels(List<String> labels) { this.labels = labels; }
    public List<Double> getValues() { return values; }
    public void setValues(List<Double> values) { this.values = values; }
}
