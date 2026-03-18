package de.bht.app.filemanagement.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Antwort-DTO für einen einzelnen CSV-Upload.
 * Enthält Metadaten über die hochgeladene und geparste Datei.
 */
public class CsvUploadResponse {

    private String filename;
    private String status;
    private String message;
    private long rowCount;
    private List<String> headers;
    private long fileSizeBytes;
    private LocalDateTime uploadedAt;

    public CsvUploadResponse() {
        this.uploadedAt = LocalDateTime.now();
    }

    /** Factory-Methode für eine erfolgreiche Antwort */
    public static CsvUploadResponse success(String filename, long rowCount,
                                            List<String> headers, long fileSizeBytes) {
        CsvUploadResponse r = new CsvUploadResponse();
        r.filename = filename;
        r.status = "OK";
        r.message = "Datei erfolgreich hochgeladen und geparst.";
        r.rowCount = rowCount;
        r.headers = headers;
        r.fileSizeBytes = fileSizeBytes;
        return r;
    }

    /** Factory-Methode für eine fehlgeschlagene Antwort */
    public static CsvUploadResponse error(String filename, String message) {
        CsvUploadResponse r = new CsvUploadResponse();
        r.filename = filename;
        r.status = "FEHLER";
        r.message = message;
        r.rowCount = 0;
        return r;
    }

    // ── Getter & Setter ──────────────────────────────────────────

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getRowCount() {
        return rowCount;
    }

    public void setRowCount(long rowCount) {
        this.rowCount = rowCount;
    }

    public List<String> getHeaders() {
        return headers;
    }

    public void setHeaders(List<String> headers) {
        this.headers = headers;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }
}

