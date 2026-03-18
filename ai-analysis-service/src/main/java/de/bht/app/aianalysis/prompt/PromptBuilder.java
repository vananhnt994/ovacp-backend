package de.bht.app.aianalysis.prompt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Baut strukturierte Prompts fuer das Gemini-Modell.
 * Nutzt serverseitig berechnete Statistiken (ueber ALLE Daten)
 * und eine kompakte Stichprobe.
 */
@Component
public class PromptBuilder {

    private static final String SYSTEM_INSTRUCTION =
            "Du bist ein Datenanalyse-Experte. Du erhaeltst CSV-Daten mit statistischen "
          + "Zusammenfassungen ueber den GESAMTEN Datensatz und eine Stichprobe der Rohdaten. "
          + "Analysiere die Daten gruendlich und gib eine klare, strukturierte Antwort auf Deutsch. "
          + "Verwende Zahlen, Statistiken und konkrete Beispiele.";

    private final int defaultSampleRows;

    public PromptBuilder(@Value("${prompt.default-sample-rows:50}") int defaultSampleRows) {
        this.defaultSampleRows = defaultSampleRows;
    }

    @SuppressWarnings("unchecked")
    public String build(String question, Map<String, CsvData> datasetsMap, Integer maxRowsPerFile) {
        StringBuilder sb = new StringBuilder();
        sb.append(SYSTEM_INSTRUCTION).append("\n\n");
        sb.append("=== DATEN ===\n\n");

        for (Map.Entry<String, CsvData> entry : datasetsMap.entrySet()) {
            String filename = entry.getKey();
            CsvData data = entry.getValue();

            sb.append("--- Datei: ").append(filename).append(" ---\n");
            sb.append("Spalten: ").append(String.join(", ", data.headers())).append("\n");
            sb.append("Gesamtzeilen: ").append(data.totalRows()).append("\n\n");

            // Serverseitige Statistiken ausgeben
            if (data.columnStats() != null && !data.columnStats().isEmpty()) {
                sb.append("STATISTIK (ueber alle ").append(data.totalRows()).append(" Zeilen):\n");
                for (Map.Entry<String, Object> colEntry : data.columnStats().entrySet()) {
                    String colName = colEntry.getKey();
                    Map<String, Object> stats = (Map<String, Object>) colEntry.getValue();
                    sb.append("  ").append(colName).append(": ");

                    String type = String.valueOf(stats.getOrDefault("type", "unknown"));
                    if ("numeric".equals(type)) {
                        sb.append(String.format("numerisch | count=%s, min=%s, max=%s, mean=%s, median=%s",
                                stats.get("count"), stats.get("min"), stats.get("max"),
                                stats.get("mean"), stats.get("median")));
                    } else {
                        sb.append(String.format("kategorisch | unique=%s", stats.get("uniqueCount")));
                        Object top5 = stats.get("top5");
                        if (top5 instanceof List<?> list && !list.isEmpty()) {
                            sb.append(", Top-5: ").append(String.join(", ",
                                    list.stream().map(String::valueOf).toList()));
                        }
                        Object allVals = stats.get("allValues");
                        if (allVals instanceof List<?> list && !list.isEmpty()) {
                            sb.append(", Werte: ").append(String.join(", ",
                                    list.stream().map(String::valueOf).toList()));
                        }
                    }
                    Object nullCount = stats.get("nullCount");
                    if (nullCount instanceof Number n && n.intValue() > 0) {
                        sb.append(", null=").append(n);
                    }
                    sb.append("\n");
                }
                sb.append("\n");
            }

            // Stichprobe
            List<List<String>> rows = data.sampleRows();
            if (!rows.isEmpty()) {
                sb.append("STICHPROBE (").append(rows.size())
                  .append(" von ").append(data.totalRows()).append(" Zeilen):\n");
                sb.append(String.join(",", data.headers())).append("\n");
                for (List<String> row : rows) {
                    sb.append(String.join(",", row)).append("\n");
                }
            }
            sb.append("\n");
        }

        sb.append("=== FRAGE ===\n");
        sb.append(question).append("\n\n");
        sb.append("Bitte antworte in ZWEI Teilen:\n\n");
        sb.append("TEIL 1 - ANALYSE:\n");
        sb.append("Ausfuehrliche, strukturierte Textantwort auf Deutsch.\n\n");
        sb.append("TEIL 2 - FERTIGE CHART-DATEN:\n");
        sb.append("Du bist der Datenanalyst! Berechne aus den obigen Statistiken und Stichproben\n");
        sb.append("die FERTIGEN Daten fuer 1-4 Diagramme. Das Frontend rendert sie direkt.\n\n");
        sb.append("Verfuegbare chartType: BAR, HISTOGRAM, PIE, HEATMAP\n\n");
        sb.append("REGELN:\n");
        sb.append("- Berechne KONKRETE ZAHLEN aus den Statistiken (mean, count, min, max, etc.).\n");
        sb.append("- Keine Platzhalter wie '...' - NUR echte berechnete Werte!\n");
        sb.append("- labels: aussagekraeftige Beschriftungen.\n");
        sb.append("- values/frequencies: konkrete Zahlenwerte.\n\n");
        sb.append("Format (exakt diese Marker verwenden):\n\n");
        sb.append("===CHARTS===\n");
        sb.append("[\n");
        sb.append("  {\n");
        sb.append("    \"chartType\": \"BAR\",\n");
        sb.append("    \"title\": \"Titel des Diagramms\",\n");
        sb.append("    \"reason\": \"Warum dieses Diagramm relevant ist\",\n");
        sb.append("    \"xAxisLabel\": \"X-Achse\",\n");
        sb.append("    \"yAxisLabel\": \"Y-Achse\",\n");
        sb.append("    \"data\": { \"labels\": [\"A\", \"B\"], \"values\": [123.4, 567.8] }\n");
        sb.append("  },\n");
        sb.append("  {\n");
        sb.append("    \"chartType\": \"HISTOGRAM\",\n");
        sb.append("    \"title\": \"Verteilung\",\n");
        sb.append("    \"reason\": \"...\",\n");
        sb.append("    \"xAxisLabel\": \"Wert\", \"yAxisLabel\": \"Anzahl\",\n");
        sb.append("    \"data\": { \"binLabels\": [\"0-100\", \"100-200\"], \"frequencies\": [500, 300] }\n");
        sb.append("  },\n");
        sb.append("  {\n");
        sb.append("    \"chartType\": \"PIE\",\n");
        sb.append("    \"title\": \"Anteile\",\n");
        sb.append("    \"reason\": \"...\",\n");
        sb.append("    \"data\": { \"labels\": [\"X\", \"Y\"], \"values\": [60.0, 40.0] }\n");
        sb.append("  }\n");
        sb.append("]\n");
        sb.append("===END_CHARTS===\n");
        sb.append("WICHTIG: Obiges ist nur FORMAT-BEISPIEL! Berechne EIGENE Werte aus den tatsaechlichen Daten!\n");
        return sb.toString();
    }

    /**
     * Record fuer CSV-Daten eines einzelnen Datensatzes.
     * columnStats kommt jetzt direkt vom File-Service (serverseitig berechnet).
     */
    public record CsvData(
            List<String> headers,
            List<List<String>> sampleRows,
            int totalRows,
            Map<String, Object> columnStats
    ) {}
}
