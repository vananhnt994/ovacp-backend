package de.bht.app.chartdata;

import de.bht.app.chartdata.model.*;
import de.bht.app.chartdata.service.ChartTransformationService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChartTransformationServiceTest {

    private final ChartTransformationService service = new ChartTransformationService();

    private CsvDataset createSampleDataset() {
        CsvDataset ds = new CsvDataset();
        ds.setFilename("test.csv");
        ds.setHeaders(List.of("Kategorie", "Umsatz", "Region"));
        ds.setRows(List.of(
                List.of("A", "100", "Nord"),
                List.of("B", "200", "Sued"),
                List.of("A", "150", "Nord"),
                List.of("C", "300", "Ost"),
                List.of("B", "250", "Nord"),
                List.of("A", "50", "Sued"),
                List.of("C", "100", "West"),
                List.of("B", "175", "Ost")
        ));
        ds.setRowCount(8);
        return ds;
    }

    @Test
    void testBarChart() {
        CsvDataset ds = createSampleDataset();
        ChartResponse<BarChartData> result = service.buildBarChart(ds, "Kategorie", "Umsatz", "SUM", null);

        assertEquals(ChartType.BAR, result.getChartType());
        assertNotNull(result.getData());
        assertEquals(3, result.getData().getLabels().size());
        // B: 200+250+175=625, C: 300+100=400, A: 100+150+50=300
        assertEquals("B", result.getData().getLabels().get(0));
        assertEquals(625.0, result.getData().getValues().get(0));
    }

    @Test
    void testBarChartCount() {
        CsvDataset ds = createSampleDataset();
        ChartResponse<BarChartData> result = service.buildBarChart(ds, "Kategorie", null, "COUNT", null);

        assertEquals(ChartType.BAR, result.getChartType());
        // A: 3, B: 3, C: 2
        assertTrue(result.getData().getValues().contains(3.0));
        assertTrue(result.getData().getValues().contains(2.0));
    }

    @Test
    void testHistogram() {
        CsvDataset ds = createSampleDataset();
        ChartResponse<HistogramData> result = service.buildHistogram(ds, "Umsatz", 5);

        assertEquals(ChartType.HISTOGRAM, result.getChartType());
        assertNotNull(result.getData());
        assertEquals(5, result.getData().getBinLabels().size());
        assertEquals(5, result.getData().getFrequencies().size());
        // Summe der Frequencies muss 8 sein (alle Zeilen)
        long total = result.getData().getFrequencies().stream().mapToLong(Long::longValue).sum();
        assertEquals(8, total);
    }

    @Test
    void testPieChart() {
        CsvDataset ds = createSampleDataset();
        ChartResponse<PieChartData> result = service.buildPieChart(ds, "Region", null, "COUNT", 10);

        assertEquals(ChartType.PIE, result.getChartType());
        assertNotNull(result.getData());
        assertTrue(result.getData().getLabels().size() >= 2);
    }

    @Test
    void testHeatmap() {
        CsvDataset ds = createSampleDataset();
        ChartResponse<HeatmapData> result = service.buildHeatmap(
                ds, "Kategorie", "Region", "Umsatz", "SUM", null);

        assertEquals(ChartType.HEATMAP, result.getChartType());
        assertNotNull(result.getData());
        assertFalse(result.getData().getXLabels().isEmpty());
        assertFalse(result.getData().getYLabels().isEmpty());
        assertEquals(result.getData().getYLabels().size(), result.getData().getMatrix().size());
    }

    @Test
    void testColumnAnalysis() {
        CsvDataset ds = createSampleDataset();
        var info = service.analyzeColumns(ds);

        assertEquals("test.csv", info.get("filename"));
        assertEquals(8L, info.get("rowCount"));
        assertNotNull(info.get("columns"));
    }

    @Test
    void testInvalidColumn() {
        CsvDataset ds = createSampleDataset();
        assertThrows(IllegalArgumentException.class,
                () -> service.buildBarChart(ds, "NichtVorhanden", null, null, null));
    }

    @Test
    void testTopN() {
        CsvDataset ds = createSampleDataset();
        ChartResponse<BarChartData> result = service.buildBarChart(ds, "Kategorie", "Umsatz", "SUM", 2);

        assertEquals(2, result.getData().getLabels().size());
    }
}

