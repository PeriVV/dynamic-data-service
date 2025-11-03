package com.example.dynamicdata.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class MultiDataSourceConfig {

    // ===== 主库属性从 spring.datasource.* 读取（Spring Boot 已帮你绑定）=====
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties mainDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "mainDataSource")
    @Primary
    public DataSource mainDataSource(DataSourceProperties props) {
        return props.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean(name = "mainJdbcTemplate")
    @Primary
    public JdbcTemplate mainJdbcTemplate(DataSource mainDataSource) {
        return new JdbcTemplate(mainDataSource);
    }

    // ===== 临时库：手动读取 sandbox.* 配置 =====
    @Bean(name = "sandboxDataSource")
    public DataSource sandboxDataSource(
            @Value("${sandbox.datasource.url}") String url,
            @Value("${sandbox.datasource.username}") String username,
            @Value("${sandbox.datasource.password}") String password
    ) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        return ds;
    }

    @Bean(name = "sandboxJdbcTemplate")
    public JdbcTemplate sandboxJdbcTemplate(@Qualifier("sandboxDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }
}
