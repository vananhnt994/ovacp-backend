package de.bht.app.chartdata.model;

import java.util.List;

/**
 * Lokales DTO zum Deserialisieren der Antwort vom File-Management-Service.
 * Spiegelt die Struktur von file-management CsvDataset wider.
 */
public class CsvDataset {

    private String filename;
    private List<String> headers;
    private List<List<String>> rows;
    private long rowCount;

    public CsvDataset() {}

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public List<String> getHeaders() { return headers; }
    public void setHeaders(List<String> headers) { this.headers = headers; }

    public List<List<String>> getRows() { return rows; }
    public void setRows(List<List<String>> rows) { this.rows = rows; }

    public long getRowCount() { return rowCount; }
    public void setRowCount(long rowCount) { this.rowCount = rowCount; }
}

