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
          + "Verwende Zahlen, Statistiken und konkrete Beispiele. "
          + "Falls du Diagramme oder Tabellen empfiehlst, beschreibe sie textuell.";

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
        sb.append("Bitte antworte ausfuehrlich und strukturiert.");
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
