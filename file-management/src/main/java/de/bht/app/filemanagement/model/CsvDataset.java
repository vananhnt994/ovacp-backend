package de.bht.app.filemanagement.model;

import java.util.List;

/**
 * Repräsentiert den geparsten Inhalt einer einzelnen CSV-Datei.
 * Wird vom AI-Analysis-Service als Eingabedaten verwendet.
 */
public class CsvDataset {

    private String filename;
    private List<String> headers;
    private List<List<String>> rows;
    private long rowCount;

    public CsvDataset() {
    }

    public CsvDataset(String filename, List<String> headers, List<List<String>> rows) {
        this.filename = filename;
        this.headers = headers;
        this.rows = rows;
        this.rowCount = rows.size();
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public List<String> getHeaders() {
        return headers;
    }

    public void setHeaders(List<String> headers) {
        this.headers = headers;
    }

    public List<List<String>> getRows() {
        return rows;
    }

    public void setRows(List<List<String>> rows) {
        this.rows = rows;
        this.rowCount = rows != null ? rows.size() : 0;
    }

    public long getRowCount() {
        return rowCount;
    }

    public void setRowCount(long rowCount) {
        this.rowCount = rowCount;
    }
}

