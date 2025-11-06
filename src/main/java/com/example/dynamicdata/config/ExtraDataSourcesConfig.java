package com.example.dynamicdata.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.*;

@Configuration
public class ExtraDataSourcesConfig {

    @Bean
    @ConfigurationProperties(prefix = "dm8.datasource")
    public Dm8Props dm8Props() { return new Dm8Props(); }

    @Bean("dm8DataSource")
    @ConditionalOnProperty(prefix = "dm8.datasource", name = "url")
    public HikariDataSource dm8DataSource(Dm8Props p) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(p.getUrl());
        ds.setUsername(p.getUsername());
        ds.setPassword(p.getPassword());
        ds.setDriverClassName(p.getDriverClassName());
        ds.setMaximumPoolSize(5);
        ds.setMinimumIdle(1);
        ds.setConnectionTimeout(15000);
        ds.setIdleTimeout(300000);
        return ds;
    }

    public static class Dm8Props {
        private String url, username, password, driverClassName, validationQuery;
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getDriverClassName() { return driverClassName; }
        public void setDriverClassName(String driverClassName) { this.driverClassName = driverClassName; }
        public String getValidationQuery() { return validationQuery; }
        public void setValidationQuery(String validationQuery) { this.validationQuery = validationQuery; }
    }
}
