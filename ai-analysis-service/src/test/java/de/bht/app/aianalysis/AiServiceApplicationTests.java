package de.bht.app.aianalysis;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "gemini.api.key=test-key"
})
class AiServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}

