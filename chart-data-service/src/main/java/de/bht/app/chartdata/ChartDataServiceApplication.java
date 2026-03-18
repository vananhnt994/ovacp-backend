package de.bht.app.chartdata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Chart Data Microservice.
 * Aggregiert CSV-Daten vom File-Management-Service und formatiert sie
 * fuer verschiedene Chart-Typen (Bar, Histogram, Heatmap, Pie).
 */
@EnableDiscoveryClient
@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
})
public class ChartDataServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChartDataServiceApplication.class, args);
    }
}

