package com.example.dynamicdata.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSetMetaData;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 动态SQL执行器服务类（支持主库与沙箱库）
 * <p>
 * 特点：
 * - 支持 SELECT / INSERT / UPDATE / DELETE 四类操作
 * - 支持 #{paramName} 参数占位符
 * - 自动防止 SQL 注入、多语句执行
 * - 可通过 useSandbox=true 路由到临时数据库执行
 */
@Service
public class DynamicSqlExecutor {

    private static final Logger logger = LoggerFactory.getLogger(DynamicSqlExecutor.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    @Qualifier("mainJdbcTemplate")
    private JdbcTemplate mainJdbcTemplate;

    @Autowired
    @Qualifier("sandboxJdbcTemplate")
    private JdbcTemplate sandboxJdbcTemplate;

    private JdbcTemplate choose(boolean useSandbox) {
        return useSandbox ? sandboxJdbcTemplate : mainJdbcTemplate;
    }
    @Autowired @Qualifier("dmJdbcTemplate")
    private JdbcTemplate dmJdbcTemplate;

    @Autowired @Qualifier("dmSandboxJdbcTemplate")
    private JdbcTemplate dmSandboxJdbcTemplate;

// 仍然保留你原来的 mainJdbcTemplate / sandboxJdbcTemplate (MySQL)

    private JdbcTemplate resolveTemplate(String kind, boolean useSandbox) {
        String k = (kind == null ? "MYSQL" : kind.trim().toUpperCase());
        return switch (k) {
            case "DM8" -> (useSandbox ? dmSandboxJdbcTemplate : dmJdbcTemplate);
            // 如你后续要接 PostgreSQL，在此扩展 POSTGRESQL 分支
            case "MYSQL" -> (useSandbox ? sandboxJdbcTemplate : mainJdbcTemplate);
            default -> throw new IllegalArgumentException("Unsupported dataSourceKind: " + kind);
        };
    }


    @Autowired
    public DynamicSqlExecutor(
            @Qualifier("mainJdbcTemplate") JdbcTemplate mainJdbcTemplate,
            @Qualifier("sandboxJdbcTemplate") JdbcTemplate sandboxJdbcTemplate, ObjectProvider<JdbcTemplate> dmJdbcProvider) {
        this.mainJdbcTemplate = mainJdbcTemplate;
        this.sandboxJdbcTemplate = sandboxJdbcTemplate;
        this.dmJdbcProvider = dmJdbcProvider;
    }


    // ===================== 核心执行入口 ===================== //

    /**
     * 查询操作（默认主库）
     */
    public List<Map<String, Object>> executeQuery(String sql, Map<String, Object> parameters) {
        return executeQuery(sql, parameters, false);
    }

    /* 查询 */
    public List<Map<String, Object>> executeQuery(String sql, Map<String, Object> params, boolean useSandbox) {
        if (!validateQuerySql(sql)) throw new IllegalArgumentException("Invalid SELECT");
        String processed = processSqlParameters(sql, params);
        Object[] values = extractParameterValues(sql, params);
        JdbcTemplate jt = choose(useSandbox);

        // 打印当前数据库名，帮助你确认到底连的是哪个库
        String db = jt.queryForObject("SELECT DATABASE()", String.class);
        logger.info("Execute QUERY on DB = {}", db);
        logger.debug("SQL={} , values={}", processed, Arrays.toString(values));

        return jt.query(processed, rs -> {
            List<Map<String, Object>> list = new ArrayList<>();
            ResultSetMetaData md = rs.getMetaData();
            int n = md.getColumnCount();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= n; i++) row.put(md.getColumnLabel(i), rs.getObject(i));
                list.add(row);
            }
            return list;
        }, values);
    }

    /* 增删改 */
    public int executeUpdate(String sql, Map<String, Object> params, boolean useSandbox) {
        if (!validateUpdateSql(sql)) throw new IllegalArgumentException("Invalid DML");
        String processed = processSqlParameters(sql, params);
        Object[] values = extractParameterValues(sql, params);
        JdbcTemplate jt = choose(useSandbox);

        String db = jt.queryForObject("SELECT DATABASE()", String.class);
        logger.info("Execute UPDATE on DB = {}", db);
        logger.debug("SQL={} , values={}", processed, Arrays.toString(values));

        return jt.update(processed, values);
    }

    /**
     * 更新操作
     */
    public int executeUpdate(String sql, Map<String, Object> parameters) {
        return executeUpdate(sql, parameters, true);
    }

    // ===================== 工具函数 ===================== //

    public String currentDb(boolean useSandbox) {
        try {
            return choose(useSandbox).queryForObject("SELECT DATABASE()", String.class);
        } catch (Exception e) {
            logger.warn("Cannot query current database name: {}", e.getMessage());
            return "UNKNOWN";
        }
    }

    /**
     * 将 #{param} 占位符替换为 ?
     */
    private String processSqlParameters(String sql, Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) return sql;
        return sql.replaceAll("#\\{\\w+\\}", "?");
    }

    /**
     * 按出现顺序提取参数值
     */
    private Object[] extractParameterValues(String sql, Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) return new Object[0];
        List<Object> values = new ArrayList<>();
        Matcher m = Pattern.compile("#\\{(\\w+)\\}").matcher(sql);
        while (m.find()) {
            String name = m.group(1);
            values.add(parameters.get(name));
        }
        return values.toArray();
    }

    /**
     * JSON参数解析
     */
    public Map<String, Object> parseParameters(String json) {
        if (json == null || json.trim().isEmpty()) return new HashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new RuntimeException("JSON parse failed: " + e.getMessage(), e);
        }
    }

    // 新增：带 dataSourceKind 参数
    public List<Map<String,Object>> executeQuery(String sql, Map<String,Object> params, String dataSourceKind, boolean useSandbox) {
        if (!validateQuerySql(sql)) throw new IllegalArgumentException("Invalid SELECT");
        String processed = processSqlParameters(sql, params);
        Object[] values = extractParameterValues(sql, params);
        JdbcTemplate jt = resolveTemplate(dataSourceKind, useSandbox);

        return jt.query(processed, rs -> {
            List<Map<String, Object>> list = new ArrayList<>();
            var md = rs.getMetaData(); int n = md.getColumnCount();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= n; i++) row.put(md.getColumnLabel(i), rs.getObject(i));
                list.add(row);
            }
            return list;
        }, values);
    }

    public int executeUpdate(String sql, Map<String,Object> params, String dataSourceKind, boolean useSandbox) {
        if (!validateUpdateSql(sql)) throw new IllegalArgumentException("Invalid DML");
        String processed = processSqlParameters(sql, params);
        Object[] values = extractParameterValues(sql, params);
        JdbcTemplate jt = resolveTemplate(dataSourceKind, useSandbox);
        return jt.update(processed, values);
    }


    // ===================== SQL安全检查 ===================== //

    /**
     * 统一SQL验证（允许 SELECT/INSERT/UPDATE/DELETE）
     */
    public boolean validateSql(String sql) {
        if (sql == null || sql.trim().isEmpty()) return false;
        String s = sql.trim().toLowerCase();

        boolean allowed = s.startsWith("select") || s.startsWith("insert")
                || s.startsWith("update") || s.startsWith("delete");
        if (!allowed) return false;

        if (s.contains(";") || s.contains("--") || s.contains("/*")) return false;

        String[] dangerous = {"drop", "alter", "truncate", "exec", "script", "xp_"};
        for (String k : dangerous) {
            if (s.contains(k)) {
                logger.warn("SQL rejected: dangerous keyword [{}]", k);
                return false;
            }
        }
        return true;
    }

    public boolean validateQuerySql(String sql) {
        return sql != null && sql.trim().toLowerCase().startsWith("select") && validateSql(sql);
    }

    public boolean validateUpdateSql(String sql) {
        String s = sql != null ? sql.trim().toLowerCase() : "";
        return (s.startsWith("insert") || s.startsWith("update") || s.startsWith("delete")) && validateSql(sql);
    }


    private final ObjectProvider<JdbcTemplate> dmJdbcProvider;

    public List<Map<String, Object>> queryOnMain(String sql) {
        return mainJdbcTemplate.queryForList(sql);
    }

    public List<Map<String, Object>> queryOnSandbox(String sql) {
        return sandboxJdbcTemplate.queryForList(sql);
    }

    public List<Map<String, Object>> queryOnDm(String sql) {
        JdbcTemplate dm = dmJdbcProvider.getIfAvailable();
        if (dm == null) {
            throw new IllegalStateException("DM8 未配置或未启用：请设置 dm8.datasource.url 后再试。");
        }
        return dm.queryForList(sql);
    }

    public String pingDm() {
        JdbcTemplate dm = dmJdbcProvider.getIfAvailable();
        if (dm == null) return "disabled";
        Integer one = dm.queryForObject("SELECT 1", Integer.class);
        return (one != null && one == 1) ? "connected" : "unhealthy";
    }
}
