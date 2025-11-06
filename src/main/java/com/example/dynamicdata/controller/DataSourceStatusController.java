package com.example.dynamicdata.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.sql.Connection;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/datasources")
@RequiredArgsConstructor
public class DataSourceStatusController {

    private final ApplicationContext ctx;
    private final Environment env;

    // 类型到“探测配置”的映射
    private record Probe(String[] beanNames, String urlProp, String probeSql) {
    }

    private static final Map<String, Probe> PROBES = Map.of(
            // MySQL：允许 mainJdbcTemplate 或 sandboxJdbcTemplate 任一存在即视为“已配置”
            "MYSQL", new Probe(new String[]{"mainJdbcTemplate", "sandboxJdbcTemplate"},
                    "spring.datasource.url",
                    "SELECT 1"),
            "DM8", new Probe(new String[]{"dmJdbcTemplate", "dmSandboxJdbcTemplate"},
                    "dm8.datasource.url",
                    "SELECT 1 FROM DUAL"),
            "POSTGRESQL", new Probe(new String[]{"postgresJdbcTemplate", "pgJdbcTemplate"},
                    "postgres.datasource.url",
                    "SELECT 1")
    );

    @GetMapping("/{type}/status")
    public ResponseEntity<Map<String, Object>> status(@PathVariable("type") String typeRaw) {
        String type = typeRaw == null ? "" : typeRaw.trim().toUpperCase(Locale.ROOT);
        Probe p = PROBES.get(type);

        // 类型未知
        if (p == null) {
            return ResponseEntity.ok(Map.of(
                    "configured", false,
                    "name", type,
                    "message", "Unknown datasource type"
            ));
        }

        // 判定“已配置”：有 url 属性 或 有任一 Bean
        boolean hasUrl = hasText(env.getProperty(p.urlProp()));
        boolean hasBean = Arrays.stream(p.beanNames()).anyMatch(ctx::containsBean);

        if (!hasUrl && !hasBean) {
            return ResponseEntity.ok(Map.of(
                    "configured", false,
                    "name", type
            ));
        }

        // 选一个实际存在的 JdbcTemplate
        JdbcTemplate jt = Arrays.stream(p.beanNames())
                .filter(ctx::containsBean)
                .findFirst()
                .map(n -> (JdbcTemplate) ctx.getBean(n))
                .orElse(null);

        // 只有有 Bean 才做连通性探测；否则仅返回“已配置但未装配 Bean”
        if (jt == null) {
            return ResponseEntity.ok(Map.of(
                    "configured", true,
                    "ok", false,
                    "message", "URL present but JdbcTemplate bean not created (check @ConditionalOnProperty and bean names)"
            ));
        }
        // 放在 try 前面：
        javax.sql.DataSource ds = jt.getDataSource();
        System.out.println("[DM8 probe] jt.ds = " + ds);
        if (ds instanceof com.zaxxer.hikari.HikariDataSource hds) {
            System.out.println("[DM8 probe] poolName=" + hds.getPoolName());
            System.out.println("[DM8 probe] jdbcUrl=" + hds.getJdbcUrl());
            System.out.println("[DM8 probe] driver=" + hds.getDriverClassName());
        }

        // 轻探测
        try {
            Integer one = jt.queryForObject(p.probeSql(), Integer.class);
            boolean ok = (one != null);
            return ResponseEntity.ok(Map.of(
                    "configured", true,
                    "ok", ok
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "configured", true,
                    "ok", false,
                    "error", e.getClass().getSimpleName(),
                    "message", String.valueOf(e.getMessage())
            ));
        }
    }

    @GetMapping("/{type}/info")
    public ResponseEntity<Map<String,Object>> info(@PathVariable("type") String typeRaw) {
        String type = typeRaw == null ? "" : typeRaw.trim().toUpperCase(Locale.ROOT);
        var p = PROBES.get(type);
        if (p == null) return ResponseEntity.ok(Map.of("configured", false, "name", type));

        boolean hasUrl = hasText(env.getProperty(p.urlProp()));
        boolean hasBean = Arrays.stream(p.beanNames()).anyMatch(ctx::containsBean);
        if (!hasUrl && !hasBean) return ResponseEntity.ok(Map.of("configured", false, "name", type));

        Map<String,Object> out = new LinkedHashMap<>();
        out.put("configured", true);
        out.put("type", type);
        out.put("url", env.getProperty(p.urlProp(), ""));

        try {
            JdbcTemplate jt = Arrays.stream(p.beanNames()).filter(ctx::containsBean)
                    .findFirst().map(n -> (JdbcTemplate) ctx.getBean(n)).orElse(null);
            if (jt != null && jt.getDataSource() instanceof com.zaxxer.hikari.HikariDataSource h) {
                out.put("poolName", h.getPoolName());
                out.put("driverClassName",
                        h.getDataSourceProperties().getProperty("driverClassName") != null
                                ? h.getDataSourceProperties().getProperty("driverClassName")
                                : h.getDriverClassName());
            }
        } catch (Exception ignore) {}
        return ResponseEntity.ok(out);
    }


    private static boolean hasText(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
