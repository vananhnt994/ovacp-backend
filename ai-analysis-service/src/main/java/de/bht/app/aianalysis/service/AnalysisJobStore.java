package de.bht.app.aianalysis.service;

import de.bht.app.aianalysis.model.AnalysisResult;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-Memory-Speicher fuer asynchrone Analyse-Jobs.
 * Speichert Ergebnisse bis sie vom Frontend abgeholt werden.
 */
@Component
public class AnalysisJobStore {

    public enum JobStatus { PROCESSING, DONE, FAILED }

    public record JobEntry(JobStatus status, AnalysisResult result) {}

    private final Map<String, JobEntry> jobs = new ConcurrentHashMap<>();

    public void markProcessing(String jobId) {
        jobs.put(jobId, new JobEntry(JobStatus.PROCESSING, null));
    }

    public void complete(String jobId, AnalysisResult result) {
        jobs.put(jobId, new JobEntry(JobStatus.DONE, result));
    }

    public void fail(String jobId, AnalysisResult result) {
        jobs.put(jobId, new JobEntry(JobStatus.FAILED, result));
    }

    public JobEntry get(String jobId) {
        return jobs.get(jobId);
    }

    public void remove(String jobId) {
        jobs.remove(jobId);
    }
}

