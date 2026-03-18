package de.bht.app.aianalysis.config;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
@Configuration
public class WebClientConfig {
    /**
     * Load-Balanced WebClient fuer Service-to-Service-Kommunikation via Eureka.
     */
    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder() {
        return WebClient.builder()
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(c -> c.defaultCodecs().maxInMemorySize(50 * 1024 * 1024)) // 50 MB
                        .build());
    }
    /**
     * Normaler WebClient fuer externe API-Aufrufe (Gemini).
     */
    @Bean("geminiWebClient")
    public WebClient geminiWebClient() {
        return WebClient.builder()
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(c -> c.defaultCodecs().maxInMemorySize(5 * 1024 * 1024))
                        .build())
                .build();
    }
}
