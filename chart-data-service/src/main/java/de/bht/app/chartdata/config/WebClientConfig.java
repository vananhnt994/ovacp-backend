package de.bht.app.chartdata.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    /**
     * Load-Balanced WebClient fuer Service-to-Service-Kommunikation via Eureka.
     * 256 MB Buffer, damit auch sehr grosse CSV-Dateien (train.csv ~40MB) geladen werden.
     */
    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder() {
        return WebClient.builder()
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(c -> c.defaultCodecs().maxInMemorySize(256 * 1024 * 1024))
                        .build());
    }
}

