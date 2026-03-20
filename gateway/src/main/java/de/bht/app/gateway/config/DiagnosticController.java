package de.bht.app.gateway.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Diagnose-Endpoint um zu pruefen ob das Gateway eigene Controller laden kann.
 */
@RestController
public class DiagnosticController {

    @GetMapping("/gateway-test")
    public Map<String, String> test() {
        return Map.of("status", "Gateway funktioniert", "timestamp", java.time.Instant.now().toString());
    }
}

