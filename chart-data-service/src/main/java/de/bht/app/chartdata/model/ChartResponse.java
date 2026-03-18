package de.bht.app.chartdata.model;
/**
 * Allgemeine Antwort-Huelle fuer alle Chart-Typen.
 *
 * @param <T> Typ der eigentlichen Chart-Daten
 */
public class ChartResponse<T> {
    private ChartType chartType;
    private String title;
    private String xAxisLabel;
    private String yAxisLabel;
    private T data;
    public ChartResponse() {}
    public ChartResponse(ChartType chartType, String title, String xAxisLabel, String yAxisLabel, T data) {
        this.chartType = chartType;
        this.title = title;
        this.xAxisLabel = xAxisLabel;
        this.yAxisLabel = yAxisLabel;
        this.data = data;
    }
    public ChartType getChartType() { return chartType; }
    public void setChartType(ChartType chartType) { this.chartType = chartType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getXAxisLabel() { return xAxisLabel; }
    public void setXAxisLabel(String xAxisLabel) { this.xAxisLabel = xAxisLabel; }
    public String getYAxisLabel() { return yAxisLabel; }
    public void setYAxisLabel(String yAxisLabel) { this.yAxisLabel = yAxisLabel; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
}
