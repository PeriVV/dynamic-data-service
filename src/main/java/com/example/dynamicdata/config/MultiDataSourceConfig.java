package com.example.dynamicdata.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class MultiDataSourceConfig {

    // =========================================================
    // MySQL - 正式库（从 spring.datasource.* 读取）
    // =========================================================
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

    @Bean(name = {"mainJdbcTemplate", "mysqlJdbcTemplate"})
    @Primary
    public JdbcTemplate mainJdbcTemplate(@Qualifier("mainDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }

    // =========================================================
    // MySQL - 沙箱库（手动配置；仅在存在 sandbox.datasource.url 时创建）
    // =========================================================
    @Bean(name = "sandboxDataSource")
    @ConditionalOnProperty(prefix = "sandbox.datasource", name = "url")
    public DataSource sandboxDataSource(
            @Value("${sandbox.datasource.url}") String url,
            @Value("${sandbox.datasource.username}") String username,
            @Value("${sandbox.datasource.password}") String password,
            @Value("${sandbox.datasource.driver-class-name:com.mysql.cj.jdbc.Driver}") String driverClassName
    ) {
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("HikariPool-sandbox");
        cfg.setJdbcUrl(url);
        cfg.setUsername(username);
        cfg.setPassword(password);
        cfg.setDriverClassName(driverClassName);
        cfg.setMinimumIdle(0);
        cfg.setMaximumPoolSize(5);
        cfg.setConnectionTimeout(5000);
        cfg.setValidationTimeout(3000);
        cfg.setIdleTimeout(60_000);
        cfg.setMaxLifetime(300_000);
        cfg.setConnectionTestQuery("SELECT 1");
        return new HikariDataSource(cfg);
    }

    @Bean(name = "sandboxJdbcTemplate")
    @ConditionalOnProperty(prefix = "sandbox.datasource", name = "url")
    public JdbcTemplate sandboxJdbcTemplate(@Qualifier("sandboxDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }

    // =========================================================
    // DM8 - 正式库（仅在 dm8.datasource.url 存在时创建；懒连接）
    // =========================================================
    @Bean(name = "dmDataSource")
    @ConditionalOnProperty(prefix = "dm8.datasource", name = "url")
    public DataSource dmDataSource(
            @Value("${dm8.datasource.url}") String url,
            @Value("${dm8.datasource.username:SYSDBA}") String username,
            @Value("${dm8.datasource.password:}") String password,
            @Value("${dm8.datasource.driver-class-name:dm.jdbc.driver.DmDriver}") String driverClassName,
            @Value("${dm8.datasource.validation-query:SELECT 1 FROM DUAL}") String testQuery
    ) {
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("HikariPool-dm8");
        cfg.setJdbcUrl(url);
        cfg.setUsername(username);
        cfg.setPassword(password);
        cfg.setDriverClassName(driverClassName);

        // 启动不快速失败，真正取连接时再连库
        cfg.setInitializationFailTimeout(-1);

        cfg.setMinimumIdle(0);
        cfg.setMaximumPoolSize(5);
        cfg.setConnectionTimeout(15_000);
        cfg.setValidationTimeout(5_000);
        cfg.setIdleTimeout(60_000);
        cfg.setMaxLifetime(300_000);
        cfg.setConnectionTestQuery(testQuery);
        return new HikariDataSource(cfg);
    }

    // 注意：不再给 dmJdbcTemplate 起 "dmSandboxJdbcTemplate" 的别名，避免混淆
    @Bean(name = {"dmJdbcTemplate", "dm8JdbcTemplate"})
    @ConditionalOnProperty(prefix = "dm8.datasource", name = "url")
    public JdbcTemplate dmJdbcTemplate(@Qualifier("dmDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }

    // =========================================================
    // DM8 - 沙箱库（仅在 dm8.sandbox.url 存在时创建；懒连接）
    // =========================================================
    @Bean(name = "dmSandboxDataSource")
    @ConditionalOnProperty(prefix = "dm8.sandbox", name = "url")
    public DataSource dmSandboxDataSource(
            @Value("${dm8.sandbox.url}") String url,
            @Value("${dm8.sandbox.username:SYSDBA}") String username,
            @Value("${dm8.sandbox.password:}") String password,
            @Value("${dm8.sandbox.driver-class-name:dm.jdbc.driver.DmDriver}") String driverClassName,
            @Value("${dm8.sandbox.validation-query:SELECT 1 FROM DUAL}") String testQuery
    ) {
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("HikariPool-dm8-sandbox");
        cfg.setJdbcUrl(url);
        cfg.setUsername(username);
        cfg.setPassword(password);
        cfg.setDriverClassName(driverClassName);

        cfg.setInitializationFailTimeout(-1);

        cfg.setMinimumIdle(0);
        cfg.setMaximumPoolSize(5);
        cfg.setConnectionTimeout(15_000);
        cfg.setValidationTimeout(5_000);
        cfg.setIdleTimeout(60_000);
        cfg.setMaxLifetime(300_000);
        cfg.setConnectionTestQuery(testQuery);
        return new HikariDataSource(cfg);
    }

    @Bean(name = "dmSandboxJdbcTemplate")
    @ConditionalOnProperty(prefix = "dm8.sandbox", name = "url")
    public JdbcTemplate dmSandboxJdbcTemplate(@Qualifier("dmSandboxDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }

    // =========================================================
    // PostgreSQL（可选；仅在 postgres.datasource.url 存在时创建）
    // =========================================================
    @Bean(name = "postgresDataSource")
    @ConditionalOnProperty(prefix = "postgres.datasource", name = "url")
    public DataSource postgresDataSource(
            @Value("${postgres.datasource.url}") String url,
            @Value("${postgres.datasource.username}") String username,
            @Value("${postgres.datasource.password}") String password,
            @Value("${postgres.datasource.driver-class-name:org.postgresql.Driver}") String driverClassName
    ) {
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("HikariPool-postgres");
        cfg.setJdbcUrl(url);
        cfg.setUsername(username);
        cfg.setPassword(password);
        cfg.setDriverClassName(driverClassName);
        cfg.setMinimumIdle(0);
        cfg.setMaximumPoolSize(5);
        cfg.setConnectionTimeout(5000);
        cfg.setValidationTimeout(3000);
        cfg.setIdleTimeout(60_000);
        cfg.setMaxLifetime(300_000);
        cfg.setConnectionTestQuery("SELECT 1");
        return new HikariDataSource(cfg);
    }

    @Bean(name = {"postgresJdbcTemplate", "pgJdbcTemplate"})
    @ConditionalOnProperty(prefix = "postgres.datasource", name = "url")
    public JdbcTemplate postgresJdbcTemplate(@Qualifier("postgresDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }

    // =========================================================
    // PostgreSQL Sandbox（可选；仅在 postgres.sandbox.url 存在时创建）
    // =========================================================
    @Bean(name = "postgresSandboxDataSource")
    @ConditionalOnProperty(prefix = "postgres.sandbox", name = "url")
    public DataSource postgresSandboxDataSource(
            @Value("${postgres.sandbox.url}") String url,
            @Value("${postgres.sandbox.username}") String username,
            @Value("${postgres.sandbox.password}") String password,
            @Value("${postgres.sandbox.driver-class-name:org.postgresql.Driver}") String driverClassName
    ) {
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("HikariPool-postgres-sandbox");
        cfg.setJdbcUrl(url);
        cfg.setUsername(username);
        cfg.setPassword(password);
        cfg.setDriverClassName(driverClassName);
        cfg.setMinimumIdle(0);
        cfg.setMaximumPoolSize(5);
        cfg.setConnectionTimeout(5000);
        cfg.setValidationTimeout(3000);
        cfg.setIdleTimeout(60_000);
        cfg.setMaxLifetime(300_000);
        cfg.setConnectionTestQuery("SELECT 1");
        return new HikariDataSource(cfg);
    }

    @Bean(name = "postgresSandboxJdbcTemplate")
    @ConditionalOnProperty(prefix = "postgres.sandbox", name = "url")
    public JdbcTemplate postgresSandboxJdbcTemplate(@Qualifier("postgresSandboxDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }
}
