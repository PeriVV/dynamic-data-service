package com.example.dynamicdata.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
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

    @Autowired(required = false)
    @Qualifier("sandboxJdbcTemplate")
    private JdbcTemplate sandboxJdbcTemplate;

    private JdbcTemplate choose(boolean useSandbox) {
        return resolveTemplate("MYSQL", useSandbox);
    }
    @Autowired(required = false) @Qualifier("dmJdbcTemplate")
    private JdbcTemplate dmJdbcTemplate;

    @Autowired(required = false) @Qualifier("dmSandboxJdbcTemplate")
    private JdbcTemplate dmSandboxJdbcTemplate;

    @Autowired(required = false) @Qualifier("postgresJdbcTemplate")
    private JdbcTemplate postgresJdbcTemplate;

    @Autowired(required = false) @Qualifier("postgresSandboxJdbcTemplate")
    private JdbcTemplate postgresSandboxJdbcTemplate;

// 仍然保留你原来的 mainJdbcTemplate / sandboxJdbcTemplate (MySQL)

    private JdbcTemplate resolveTemplate(String kind, boolean useSandbox) {
        String k = (kind == null ? "MYSQL" : kind.trim().toUpperCase());
        return switch (k) {
            case "DM8" -> requireTemplate(useSandbox ? dmSandboxJdbcTemplate : dmJdbcTemplate, k, useSandbox);
            case "POSTGRESQL" -> requireTemplate(useSandbox ? postgresSandboxJdbcTemplate : postgresJdbcTemplate, k, useSandbox);
            case "MYSQL" -> requireTemplate(useSandbox ? sandboxJdbcTemplate : mainJdbcTemplate, k, useSandbox);
            default -> throw new IllegalArgumentException("Unsupported dataSourceKind: " + kind);
        };
    }

    private JdbcTemplate requireTemplate(JdbcTemplate jt, String kind, boolean useSandbox) {
        if (jt != null) {
            return jt;
        }
        String targetProp = switch (kind) {
            case "DM8" -> useSandbox ? "dm8.sandbox.url" : "dm8.datasource.url";
            case "POSTGRESQL" -> useSandbox ? "postgres.sandbox.url" : "postgres.datasource.url";
            case "MYSQL" -> useSandbox ? "sandbox.datasource.url" : "spring.datasource.url";
            default -> "datasource.url";
        };
        throw new IllegalStateException(kind + " datasource not configured (" + targetProp + ")");
    }


    @Autowired
    public DynamicSqlExecutor(
            @Qualifier("mainJdbcTemplate") JdbcTemplate mainJdbcTemplate) {
        this.mainJdbcTemplate = mainJdbcTemplate;
    }


    // ===================== 核心执行入口 ===================== //

    /**
     * 查询操作（默认主库）
     */
    public List<Map<String, Object>> executeQuery(String sql, Map<String, Object> parameters) {
        return executeQuery(sql, parameters, false);
    }

    /* 查询（MySQL / DM8，useSandbox 版本） */
    public List<Map<String, Object>> executeQuery(String sql, Map<String, Object> params, boolean useSandbox) {
        if (!validateQuerySql(sql)) throw new IllegalArgumentException("Invalid SELECT");
        String processed = processSqlParameters(sql, params);
        Object[] values = extractParameterValues(sql, params);
        JdbcTemplate jt = choose(useSandbox);

        logger.debug("EXECUTE QUERY: sql={}, values={}", processed, Arrays.toString(values));
        try {
            return jt.query(processed, rs -> {
                List<Map<String, Object>> list = new ArrayList<>();
                ResultSetMetaData md = rs.getMetaData();
                int n = md.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= n; i++) {
                        row.put(md.getColumnLabel(i), rs.getObject(i));
                    }
                    list.add(row);
                }
                return list;
            }, values);
        } catch (DataAccessException e) {
            Throwable root = e.getMostSpecificCause();
            String msg = (root != null ? root.getMessage() : e.getMessage());
            logger.error("Execute query failed. sql={}, values={}, error={}",
                    processed, Arrays.toString(values), msg);
            // 抛一个带“干净”文案的异常，给 GlobalExceptionHandler 用
            throw new RuntimeException("Error executing SQL: " + msg, e);
        }
    }

    /* 增删改（useSandbox 版本） */
    public int executeUpdate(String sql, Map<String, Object> params, boolean useSandbox) {

        String s = sql.trim().toLowerCase(Locale.ROOT);

        // DML + DDL 全部走 executeUpdate
        boolean isSelect = s.startsWith("select");
        if (isSelect) {
            throw new IllegalArgumentException("Invalid DML: SELECT 请使用 executeQuery()");
        }

        if (!validateUpdateSql(sql)) {
            throw new IllegalArgumentException("Invalid DML: 不支持的 SQL 操作 -> " + sql);
        }

        String processed = processSqlParameters(sql, params);
        Object[] values = extractParameterValues(sql, params);
        JdbcTemplate jt = choose(useSandbox);

        logger.debug("EXECUTE UPDATE: sql={}, values={}", processed, Arrays.toString(values));

        try {
            return jt.update(processed, values);
        } catch (DataAccessException e) {
            Throwable root = e.getMostSpecificCause();
            String msg = (root != null ? root.getMessage() : e.getMessage());
            logger.error("Execute update failed. sql={}, values={}, error={}",
                    processed, Arrays.toString(values), msg);
            throw new RuntimeException("Error executing SQL: " + msg, e);
        }
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

    public String currentDb(String dataSourceKind, boolean useSandbox) {
        String kind = (dataSourceKind == null ? "MYSQL" : dataSourceKind.trim().toUpperCase());
        try {
            JdbcTemplate jt = resolveTemplate(kind, useSandbox);
            return switch (kind) {
                case "DM8" -> {
                    // DM 类 Oracle，用 SYS_CONTEXT 取当前模式
                    String schema = jt.queryForObject(
                            "SELECT SYS_CONTEXT('USERENV','CURRENT_SCHEMA') AS CUR_SCHEMA FROM DUAL",
                            String.class
                    );
                    yield schema;
                }
                case "POSTGRESQL" -> jt.queryForObject("SELECT current_database()", String.class);
                default -> jt.queryForObject("SELECT DATABASE()", String.class); // MySQL
            };
        } catch (Exception e) {
            logger.warn("Cannot query current database name for kind {}: {}", kind, e.getMessage());
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

        String trimmed = sql.trim();
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }

        String s = trimmed.toLowerCase(Locale.ROOT);

        // ===== 允许的 SQL 类型 =====
        boolean allowed =
                s.startsWith("select") ||
                        s.startsWith("insert") ||
                        s.startsWith("update") ||
                        s.startsWith("delete") ||
                        s.startsWith("create") ||
                        s.startsWith("alter") ||
                        s.startsWith("drop") ||
                        s.startsWith("truncate");

        if (!allowed) return false;

        // ===== 禁止多语句、注释 =====
        if (s.contains(";") || s.contains("--") || s.contains("/*")) return false;

        return true;
    }


    public boolean validateQuerySql(String sql) {
        return sql != null && sql.trim().toLowerCase().startsWith("select") && validateSql(sql);
    }

    public boolean validateUpdateSql(String sql) {
        if (sql == null) return false;
        String s = sql.trim().toLowerCase(Locale.ROOT);

        // DML
        if (s.startsWith("insert") || s.startsWith("update") || s.startsWith("delete"))
            return validateSql(sql);

        // DDL —— 新增支持
        if (s.startsWith("create") || s.startsWith("alter") || s.startsWith("drop") || s.startsWith("truncate"))
            return validateSql(sql);

        return false;
    }



    @Autowired
    private ObjectProvider<JdbcTemplate> dmJdbcProvider;

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
