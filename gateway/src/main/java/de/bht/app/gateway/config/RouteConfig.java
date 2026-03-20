package de.bht.app.gateway.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

import java.net.URI;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.setRequestHostHeader;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.path;

/**
 * Programmatische Gateway-Routen.
 * URIs konfigurierbar via Umgebungsvariablen (Cloud Run / Docker) oder application.properties (lokal).
 *
 * Lokal:      http://localhost:PORT  (Standard – kein Eureka noetig)
 * Docker:     lb://service-name      (via ROUTE_* Env-Variablen, Eureka noetig)
 * Cloud Run:  https://service-xxx.run.app (via ROUTE_* Env-Variablen)
 */
@Configuration
public class RouteConfig {

    private static final Logger log = LoggerFactory.getLogger(RouteConfig.class);

    @Value("${route.usermanagement:http://localhost:8081}")
    private String usermanagementUri;

    @Value("${route.file-management:http://localhost:8082}")
    private String fileManagementUri;

    @Value("${route.ai-analysis:http://localhost:8083}")
    private String aiAnalysisUri;

    @Value("${route.chart-data:http://localhost:8084}")
    private String chartDataUri;

    @PostConstruct
    void logRoutes() {
        log.info("=== Gateway Route Configuration ===");
        log.info("  usermanagement -> {}", usermanagementUri);
        log.info("  file-management -> {}", fileManagementUri);
        log.info("  ai-analysis -> {}", aiAnalysisUri);
        log.info("  chart-data -> {}", chartDataUri);
        log.info("===================================");
    }

    /** Test: Plain RouterFunction ohne Spring Cloud Gateway */
    @Bean
    public RouterFunction<ServerResponse> plainTestRoute() {
        return RouterFunctions.route()
                .GET("/route-test", request -> ServerResponse.ok().body("RouterFunction works!"))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> usermanagementRoute() {
        return buildRoute("usermanagement-route",
                new String[]{"/api/users", "/api/users/**"},
                usermanagementUri);
    }

    @Bean
    public RouterFunction<ServerResponse> fileManagementRoute() {
        return buildRoute("file-management-route",
                new String[]{"/api/files", "/api/files/**"},
                fileManagementUri);
    }

    @Bean
    public RouterFunction<ServerResponse> aiAnalysisRoute() {
        return buildRoute("ai-analysis-route",
                new String[]{"/api/analysis", "/api/analysis/**"},
                aiAnalysisUri);
    }

    @Bean
    public RouterFunction<ServerResponse> chartDataRoute() {
        return buildRoute("chart-data-route",
                new String[]{"/api/charts", "/api/charts/**"},
                chartDataUri);
    }

    /**
     * Baut eine Gateway-Route mit den gegebenen Pfad-Patterns und Ziel-URI.
     * setRequestHostHeader wird nur fuer direkte HTTP(S)-URIs gesetzt (Cloud Run),
     * NICHT fuer lb:// URIs (Eureka Load Balancer uebernimmt das selbst).
     */
    private RouterFunction<ServerResponse> buildRoute(String id, String[] paths, String targetUri) {
        var builder = route(id)
                .route(path(paths), HandlerFunctions.http(targetUri));

        // Request-Logging
        builder.before(request -> {
            log.debug(">> [{}] {} {} -> {}", id, request.method(), request.uri(), targetUri);
            return request;
        });

        // Host-Header nur fuer direkte HTTP(S)-URIs setzen (z.B. Cloud Run)
        // Bei lb:// loest der LoadBalancer den Host selbst auf
        if (!targetUri.startsWith("lb://")) {
            builder.before(setRequestHostHeader(extractHost(targetUri)));
        }

        // Response-Logging (inkl. Fehler)
        builder.after((request, response) -> {
            if (response.statusCode().isError()) {
                log.warn("<< [{}] {} {} -> {} (Status {})",
                        id, request.method(), request.uri(), targetUri, response.statusCode().value());
            }
            return response;
        });

        return builder.build();
    }

    private static String extractHost(String uri) {
        try {
            return URI.create(uri).getHost();
        } catch (Exception e) {
            return "localhost";
        }
    }
}
