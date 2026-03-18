package de.bht.app.chartdata.service;

import de.bht.app.chartdata.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Transformiert CSV-Rohdaten (headers + rows) in chart-fertige Datenstrukturen.
 * Unterstuetzt: Bar-Chart, Histogramm, Heatmap, Pie-Chart.
 */
@Service
public class ChartTransformationService {

    private static final Logger log = LoggerFactory.getLogger(ChartTransformationService.class);

    // ════════════════════════════════════════════════════════════════
    //  BAR CHART
    // ════════════════════════════════════════════════════════════════

    /**
     * Erstellt ein Balkendiagramm.
     *
     * @param dataset      CSV-Daten
     * @param column       Kategorie-Spalte (X-Achse, z.B. "StoreType")
     * @param valueColumn  Wert-Spalte (Y-Achse, z.B. "Sales"). Wenn null -> COUNT
     * @param aggregation  SUM | AVG | COUNT | MIN | MAX (Default: SUM)
     * @param topN         Nur die Top-N Kategorien (null = alle)
     */
    public ChartResponse<BarChartData> buildBarChart(CsvDataset dataset,
                                                     String column,
                                                     String valueColumn,
                                                     String aggregation,
                                                     Integer topN) {
        log.debug("Bar-Chart: column={}, valueColumn={}, agg={}, topN={}",
                column, valueColumn, aggregation, topN);

        int catIdx = getColumnIndex(dataset, column);
        int valIdx = valueColumn != null ? getColumnIndex(dataset, valueColumn) : -1;
        String agg = aggregation != null ? aggregation.toUpperCase() : (valIdx >= 0 ? "SUM" : "COUNT");

        // Gruppieren
        Map<String, List<Double>> grouped = new LinkedHashMap<>();
        for (List<String> row : dataset.getRows()) {
            if (catIdx >= row.size()) continue;
            String key = row.get(catIdx);
            if (key == null || key.isBlank()) key = "(leer)";

            double val = 1.0; // fuer COUNT
            if (valIdx >= 0 && valIdx < row.size()) {
                val = parseDouble(row.get(valIdx));
            }
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(val);
        }

        // Aggregieren
        Map<String, Double> aggregated = new LinkedHashMap<>();
        for (var entry : grouped.entrySet()) {
            aggregated.put(entry.getKey(), aggregate(entry.getValue(), agg));
        }

        // Sortieren (absteigend) und optional Top-N
        List<Map.Entry<String, Double>> sorted = aggregated.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .collect(Collectors.toList());

        if (topN != null && topN > 0 && topN < sorted.size()) {
            sorted = sorted.subList(0, topN);
        }

        List<String> labels = sorted.stream().map(Map.Entry::getKey).collect(Collectors.toList());
        List<Double> values = sorted.stream().map(Map.Entry::getValue).collect(Collectors.toList());

        String title = valueColumn != null
                ? agg + "(" + valueColumn + ") nach " + column
                : "Anzahl nach " + column;

        return new ChartResponse<>(
                ChartType.BAR, title, column,
                valueColumn != null ? agg + "(" + valueColumn + ")" : "Anzahl",
                new BarChartData(labels, values)
        );
    }

    // ════════════════════════════════════════════════════════════════
    //  HISTOGRAM
    // ════════════════════════════════════════════════════════════════

    /**
     * Erstellt ein Histogramm fuer eine numerische Spalte.
     *
     * @param dataset CSV-Daten
     * @param column  Numerische Spalte (z.B. "Sales")
     * @param bins    Anzahl Bins (Default: 10)
     */
    public ChartResponse<HistogramData> buildHistogram(CsvDataset dataset,
                                                       String column,
                                                       Integer bins) {
        int colIdx = getColumnIndex(dataset, column);
        int numBins = bins != null && bins > 0 ? bins : 10;
        log.debug("Histogramm: column={}, bins={}", column, numBins);

        // Alle numerischen Werte sammeln
        List<Double> values = new ArrayList<>();
        for (List<String> row : dataset.getRows()) {
            if (colIdx >= row.size()) continue;
            try {
                double v = Double.parseDouble(row.get(colIdx).trim());
                values.add(v);
            } catch (NumberFormatException ignored) {}
        }

        if (values.isEmpty()) {
            throw new IllegalArgumentException(
                    "Spalte '" + column + "' enthaelt keine numerischen Werte fuer ein Histogramm.");
        }

        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0);

        // Edge-Case: alle Werte gleich
        if (min == max) {
            max = min + 1;
        }

        double binWidth = (max - min) / numBins;
        long[] frequencies = new long[numBins];
        List<String> binLabels = new ArrayList<>();

        for (int i = 0; i < numBins; i++) {
            double lo = min + i * binWidth;
            double hi = lo + binWidth;
            binLabels.add(String.format("%.1f - %.1f", lo, hi));
        }

        for (double v : values) {
            int idx = (int) ((v - min) / binWidth);
            if (idx >= numBins) idx = numBins - 1; // max-Wert in letzten Bin
            if (idx < 0) idx = 0;
            frequencies[idx]++;
        }

        List<Long> freqList = new ArrayList<>();
        for (long f : frequencies) freqList.add(f);

        return new ChartResponse<>(
                ChartType.HISTOGRAM,
                "Verteilung von " + column,
                column,
                "Haeufigkeit",
                new HistogramData(binLabels, freqList)
        );
    }

    // ════════════════════════════════════════════════════════════════
    //  HEATMAP
    // ════════════════════════════════════════════════════════════════

    /**
     * Erstellt eine Heatmap (Kreuztabelle) aus zwei kategorialen Spalten.
     * Der Wert in jeder Zelle ist die Aggregation einer dritten Spalte (oder COUNT).
     *
     * @param dataset     CSV-Daten
     * @param xColumn     Spalte fuer X-Achse
     * @param yColumn     Spalte fuer Y-Achse
     * @param valueColumn Wert-Spalte (optional, null = COUNT)
     * @param aggregation Aggregation (Default: SUM)
     * @param topN        Max. Kategorien pro Achse (null = alle, max 30)
     */
    public ChartResponse<HeatmapData> buildHeatmap(CsvDataset dataset,
                                                    String xColumn,
                                                    String yColumn,
                                                    String valueColumn,
                                                    String aggregation,
                                                    Integer topN) {
        int xIdx = getColumnIndex(dataset, xColumn);
        int yIdx = getColumnIndex(dataset, yColumn);
        int valIdx = valueColumn != null ? getColumnIndex(dataset, valueColumn) : -1;
        String agg = aggregation != null ? aggregation.toUpperCase() : (valIdx >= 0 ? "SUM" : "COUNT");
        int limit = topN != null && topN > 0 ? topN : 30;

        log.debug("Heatmap: x={}, y={}, val={}, agg={}, topN={}", xColumn, yColumn, valueColumn, agg, limit);

        // Rohdaten in Map: (xKey, yKey) -> Liste<Value>
        Map<String, Map<String, List<Double>>> grid = new LinkedHashMap<>();
        Map<String, Double> xTotals = new LinkedHashMap<>();
        Map<String, Double> yTotals = new LinkedHashMap<>();

        for (List<String> row : dataset.getRows()) {
            if (xIdx >= row.size() || yIdx >= row.size()) continue;
            String xKey = row.get(xIdx);
            String yKey = row.get(yIdx);
            if (xKey == null || xKey.isBlank()) xKey = "(leer)";
            if (yKey == null || yKey.isBlank()) yKey = "(leer)";

            double val = 1.0;
            if (valIdx >= 0 && valIdx < row.size()) {
                val = parseDouble(row.get(valIdx));
            }

            grid.computeIfAbsent(xKey, k -> new LinkedHashMap<>())
                    .computeIfAbsent(yKey, k -> new ArrayList<>())
                    .add(val);

            xTotals.merge(xKey, val, Double::sum);
            yTotals.merge(yKey, val, Double::sum);
        }

        // Top-N Kategorien nach Gesamtwert
        List<String> xLabels = xTotals.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        List<String> yLabels = yTotals.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // Matrix aufbauen
        List<List<Double>> matrix = new ArrayList<>();
        for (String yKey : yLabels) {
            List<Double> row = new ArrayList<>();
            for (String xKey : xLabels) {
                List<Double> vals = grid.getOrDefault(xKey, Collections.emptyMap())
                        .getOrDefault(yKey, Collections.emptyList());
                row.add(vals.isEmpty() ? 0.0 : aggregate(vals, agg));
            }
            matrix.add(row);
        }

        String title = valueColumn != null
                ? agg + "(" + valueColumn + "): " + xColumn + " x " + yColumn
                : "COUNT: " + xColumn + " x " + yColumn;

        return new ChartResponse<>(
                ChartType.HEATMAP, title, xColumn, yColumn,
                new HeatmapData(xLabels, yLabels, matrix)
        );
    }

    // ════════════════════════════════════════════════════════════════
    //  PIE CHART
    // ════════════════════════════════════════════════════════════════

    /**
     * Erstellt ein Tortendiagramm.
     *
     * @param dataset     CSV-Daten
     * @param column      Kategorie-Spalte
     * @param valueColumn Wert-Spalte (optional, null = COUNT)
     * @param aggregation Aggregation (Default: SUM)
     * @param topN        Top-N Segmente (Rest wird zu "Sonstige" zusammengefasst)
     */
    public ChartResponse<PieChartData> buildPieChart(CsvDataset dataset,
                                                     String column,
                                                     String valueColumn,
                                                     String aggregation,
                                                     Integer topN) {
        log.debug("Pie-Chart: column={}, valueColumn={}, agg={}, topN={}",
                column, valueColumn, aggregation, topN);

        int catIdx = getColumnIndex(dataset, column);
        int valIdx = valueColumn != null ? getColumnIndex(dataset, valueColumn) : -1;
        String agg = aggregation != null ? aggregation.toUpperCase() : (valIdx >= 0 ? "SUM" : "COUNT");

        // Gruppieren
        Map<String, List<Double>> grouped = new LinkedHashMap<>();
        for (List<String> row : dataset.getRows()) {
            if (catIdx >= row.size()) continue;
            String key = row.get(catIdx);
            if (key == null || key.isBlank()) key = "(leer)";

            double val = 1.0;
            if (valIdx >= 0 && valIdx < row.size()) {
                val = parseDouble(row.get(valIdx));
            }
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(val);
        }

        // Aggregieren und sortieren
        Map<String, Double> aggregated = new LinkedHashMap<>();
        for (var entry : grouped.entrySet()) {
            aggregated.put(entry.getKey(), aggregate(entry.getValue(), agg));
        }

        List<Map.Entry<String, Double>> sorted = aggregated.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .collect(Collectors.toList());

        // Top-N + "Sonstige"
        int limit = (topN != null && topN > 0) ? topN : 10;
        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();

        double othersTotal = 0;
        for (int i = 0; i < sorted.size(); i++) {
            if (i < limit) {
                labels.add(sorted.get(i).getKey());
                values.add(sorted.get(i).getValue());
            } else {
                othersTotal += sorted.get(i).getValue();
            }
        }
        if (othersTotal > 0) {
            labels.add("Sonstige");
            values.add(othersTotal);
        }

        String title = valueColumn != null
                ? agg + "(" + valueColumn + ") nach " + column
                : "Verteilung nach " + column;

        return new ChartResponse<>(
                ChartType.PIE, title, column, null,
                new PieChartData(labels, values)
        );
    }

    // ════════════════════════════════════════════════════════════════
    //  SPALTEN-INFO (fuer Frontend: welche Spalten verfuegbar?)
    // ════════════════════════════════════════════════════════════════

    /**
     * Analysiert die Spalten einer CSV-Datei und gibt zurueck,
     * welche numerisch und welche kategorial sind.
     */
    public Map<String, Object> analyzeColumns(CsvDataset dataset) {
        List<String> headers = dataset.getHeaders();
        List<Map<String, Object>> columns = new ArrayList<>();

        for (int i = 0; i < headers.size(); i++) {
            Map<String, Object> colInfo = new LinkedHashMap<>();
            colInfo.put("name", headers.get(i));

            // Stichprobe pruefen ob numerisch
            int numericCount = 0;
            int sampleSize = Math.min(100, dataset.getRows().size());
            Set<String> uniqueValues = new LinkedHashSet<>();

            for (int r = 0; r < sampleSize; r++) {
                List<String> row = dataset.getRows().get(r);
                if (i < row.size() && row.get(i) != null && !row.get(i).isBlank()) {
                    try {
                        Double.parseDouble(row.get(i).trim());
                        numericCount++;
                    } catch (NumberFormatException ignored) {}
                    uniqueValues.add(row.get(i).trim());
                }
            }

            boolean isNumeric = numericCount > sampleSize * 0.7;
            colInfo.put("type", isNumeric ? "numeric" : "categorical");
            colInfo.put("uniqueSample", Math.min(uniqueValues.size(), 20));

            // Fuer kategoriale Spalten: die haeufigsten Werte
            if (!isNumeric && uniqueValues.size() <= 50) {
                colInfo.put("sampleValues", uniqueValues.stream().limit(10).collect(Collectors.toList()));
            }

            columns.add(colInfo);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("filename", dataset.getFilename());
        result.put("rowCount", dataset.getRowCount());
        result.put("columns", columns);
        return result;
    }

    // ════════════════════════════════════════════════════════════════
    //  HILFSMETHODEN
    // ════════════════════════════════════════════════════════════════

    private int getColumnIndex(CsvDataset dataset, String columnName) {
        List<String> headers = dataset.getHeaders();
        for (int i = 0; i < headers.size(); i++) {
            if (headers.get(i).equalsIgnoreCase(columnName)) {
                return i;
            }
        }
        throw new IllegalArgumentException(
                "Spalte '" + columnName + "' nicht gefunden. Verfuegbar: " + headers);
    }

    private double parseDouble(String s) {
        if (s == null || s.isBlank()) return 0.0;
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private double aggregate(List<Double> values, String agg) {
        if (values.isEmpty()) return 0.0;
        return switch (agg) {
            case "SUM" -> values.stream().mapToDouble(Double::doubleValue).sum();
            case "AVG" -> values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            case "COUNT" -> (double) values.size();
            case "MIN" -> values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
            case "MAX" -> values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            default -> values.stream().mapToDouble(Double::doubleValue).sum();
        };
    }
}

