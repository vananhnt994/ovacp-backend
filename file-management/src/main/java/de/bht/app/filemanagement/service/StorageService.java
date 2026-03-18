package de.bht.app.filemanagement.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service zum Speichern, Auflisten und Löschen von hochgeladenen CSV-Dateien
 * auf dem lokalen Dateisystem.
 */
@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private final Path rootLocation;

    public StorageService(@Value("${file.upload-dir:./uploads}") String uploadDir) {
        this.rootLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    /** Erstellt das Upload-Verzeichnis beim Start, falls es noch nicht existiert. */
    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(rootLocation);
            log.info("Upload-Verzeichnis: {}", rootLocation);
        } catch (IOException e) {
            throw new RuntimeException("Upload-Verzeichnis konnte nicht erstellt werden: " + rootLocation, e);
        }
    }

    /**
     * Speichert eine hochgeladene Datei.
     * Existierende Dateien mit gleichem Namen werden überschrieben.
     *
     * @param file die hochgeladene MultipartFile
     * @return der Pfad zur gespeicherten Datei
     */
    public Path store(MultipartFile file) throws IOException {
        String filename = sanitizeFilename(file.getOriginalFilename());
        if (filename.isBlank()) {
            throw new IllegalArgumentException("Dateiname darf nicht leer sein.");
        }

        Path destinationFile = rootLocation.resolve(filename).normalize();

        // Sicherheitscheck: Pfad darf nicht außerhalb des Upload-Verzeichnisses liegen
        if (!destinationFile.startsWith(rootLocation)) {
            throw new SecurityException("Datei darf nicht außerhalb des Upload-Verzeichnisses gespeichert werden.");
        }

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
        }

        log.info("Datei gespeichert: {} ({} Bytes)", filename, file.getSize());
        return destinationFile;
    }

    /**
     * Gibt eine Liste aller gespeicherten CSV-Dateinamen zurück.
     */
    public List<String> listAll() throws IOException {
        try (Stream<Path> paths = Files.list(rootLocation)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(".csv"))
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    /**
     * Gibt den vollständigen Pfad zu einer gespeicherten Datei zurück.
     *
     * @param filename Dateiname
     * @return Pfad zur Datei
     * @throws NoSuchFileException wenn die Datei nicht existiert
     */
    public Path load(String filename) throws IOException {
        Path file = rootLocation.resolve(sanitizeFilename(filename)).normalize();
        if (!Files.exists(file)) {
            throw new NoSuchFileException("Datei nicht gefunden: " + filename);
        }
        return file;
    }

    /**
     * Löscht eine gespeicherte Datei.
     *
     * @param filename Dateiname
     * @return true, wenn die Datei gelöscht wurde
     */
    public boolean delete(String filename) throws IOException {
        Path file = rootLocation.resolve(sanitizeFilename(filename)).normalize();
        if (!file.startsWith(rootLocation)) {
            throw new SecurityException("Ungültiger Dateipfad.");
        }
        return Files.deleteIfExists(file);
    }

    /**
     * Gibt die Dateigröße in Bytes zurück.
     */
    public long fileSize(String filename) throws IOException {
        return Files.size(load(filename));
    }

    /** Entfernt potenziell gefährliche Zeichen aus dem Dateinamen. */
    private String sanitizeFilename(String original) {
        if (original == null) return "";
        // Nur Dateiname behalten (kein Pfad)
        String name = Paths.get(original).getFileName().toString();
        // Sonderzeichen entfernen, nur Buchstaben, Ziffern, Punkt, Bindestrich, Unterstrich erlaubt
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}

