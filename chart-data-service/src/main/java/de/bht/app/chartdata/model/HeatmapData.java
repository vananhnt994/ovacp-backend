package de.bht.app.chartdata.model;
import java.util.List;
/**
 * Daten fuer eine Heatmap.
 * matrix[y][x] enthaelt den aggregierten Wert fuer (xLabels[x], yLabels[y]).
 */
public class HeatmapData {
    private List<String> xLabels;
    private List<String> yLabels;
    private List<List<Double>> matrix;
    public HeatmapData() {}
    public HeatmapData(List<String> xLabels, List<String> yLabels, List<List<Double>> matrix) {
        this.xLabels = xLabels;
        this.yLabels = yLabels;
        this.matrix = matrix;
    }
    public List<String> getXLabels() { return xLabels; }
    public void setXLabels(List<String> xLabels) { this.xLabels = xLabels; }
    public List<String> getYLabels() { return yLabels; }
    public void setYLabels(List<String> yLabels) { this.yLabels = yLabels; }
    public List<List<Double>> getMatrix() { return matrix; }
    public void setMatrix(List<List<Double>> matrix) { this.matrix = matrix; }
}
