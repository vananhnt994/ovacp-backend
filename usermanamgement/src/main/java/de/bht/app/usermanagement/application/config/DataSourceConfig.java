// src/main/java/de/bht/app/usermanagement/config/DataSourceConfig.java
package de.bht.app.usermanagement.application.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    @Value("${spring.datasource.url:}")
    private String url;

    @Value("${spring.cloud.gcp.sql.instance-connection-name:}")
    private String instanceConnectionName;

    @Value("${spring.cloud.gcp.sql.database-name:}")
    private String databaseName;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${spring.datasource.driver-class-name:org.postgresql.Driver}")
    private String driverClassName;

    @Bean
    public DataSource dataSource() {
        String effectiveUrl = resolveJdbcUrl();

        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setUrl(effectiveUrl);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName(driverClassName);
        return ds;
    }

    private String resolveJdbcUrl() {
        if (StringUtils.hasText(url)) {
            return url;
        }

        if (StringUtils.hasText(instanceConnectionName) && StringUtils.hasText(databaseName)) {
            return "jdbc:postgresql:///" + databaseName
                    + "?cloudSqlInstance=" + instanceConnectionName
                    + "&socketFactory=com.google.cloud.sql.postgres.SocketFactory";
        }

        throw new IllegalStateException(
                "Missing DB configuration: set 'spring.datasource.url' or both "
                        + "'spring.cloud.gcp.sql.instance-connection-name' and "
                        + "'spring.cloud.gcp.sql.database-name'."
        );
    }
}