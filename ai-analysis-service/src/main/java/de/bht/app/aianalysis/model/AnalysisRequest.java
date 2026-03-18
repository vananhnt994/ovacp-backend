package de.bht.app.aianalysis.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.List;

/**
 * DTO fuer eine Analyse-Anfrage vom Frontend.
 * Akzeptiert verschiedene Feldnamen (deutsch/englisch) via @JsonAlias.
 */
public class AnalysisRequest {

    /** Liste der zu analysierenden CSV-Dateinamen */
    @JsonAlias({"fileNames", "dateien", "files"})
    private List<String> filenames;

    /** Die Frage / Aufgabe des Nutzers */
    @JsonAlias({"query", "frage", "prompt"})
    private String question;

    /** Optionales Gemini-Modell (default: gemini-2.0-flash) */
    private String model;

    /** Optionales Zeilenlimit pro Datei, um Token-Kosten zu begrenzen */
    private Integer maxRows;

    public AnalysisRequest() {
    }

    public AnalysisRequest(List<String> filenames, String question) {
        this.filenames = filenames;
        this.question = question;
    }

    public List<String> getFilenames() {
        return filenames;
    }

    public void setFilenames(List<String> filenames) {
        this.filenames = filenames;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Integer getMaxRows() {
        return maxRows;
    }

    public void setMaxRows(Integer maxRows) {
        this.maxRows = maxRows;
    }
}
