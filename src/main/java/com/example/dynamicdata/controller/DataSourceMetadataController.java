package com.example.dynamicdata.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/datasources")
@RequiredArgsConstructor
public class DataSourceMetadataController {

    private final ApplicationContext ctx;

    /**
     * 简单缓存表名，减少频繁访问系统表的开销
     * key: 数据源类型，例如 MYSQL / DM8 / POSTGRESQL
     */
    private final Map<String, CacheEntry> tableCache = new HashMap<>();

    /**
     * 缓存结构体
     */
    private static class CacheEntry {
        final long timestamp;
        final Set<String> tables;

        CacheEntry(Set<String> tables) {
            this.timestamp = System.currentTimeMillis();
            this.tables = tables;
        }
    }

    /**
     * 查询并缓存表名
     */
    private Set<String> listAllTableNamesCached(String type, JdbcTemplate jt) {
        long now = System.currentTimeMillis();
        CacheEntry entry = tableCache.get(type.toUpperCase(Locale.ROOT));
        // 缓存有效期 60 秒
        if (entry != null && now - entry.timestamp < 60_000) {
            return entry.tables;
        }

        Set<String> tables = new HashSet<>();
        try {
            if ("DM8".equalsIgnoreCase(type)) {
                String owner = jt.queryForObject(
                        "SELECT SYS_CONTEXT('USERENV','CURRENT_SCHEMA') FROM DUAL",
                        String.class
                );


                List<Map<String, Object>> list = jt.queryForList(
                        "SELECT TABLE_NAME FROM ALL_TABLES WHERE OWNER = ?",
                        owner.toUpperCase(Locale.ROOT)
                );

                for (Map<String, Object> row : list) {
                    Object t = row.get("TABLE_NAME");
                    if (t != null) tables.add(String.valueOf(t));
                }
            } else if ("POSTGRESQL".equalsIgnoreCase(type)) {
                List<Map<String, Object>> list = jt.queryForList(
                        "SELECT tablename FROM pg_tables WHERE schemaname NOT IN ('pg_catalog', 'information_schema')");
                for (Map<String, Object> row : list) {
                    Object t = row.get("tablename");
                    if (t != null) tables.add(String.valueOf(t));
                }
            } else { // 默认 MySQL
                List<Map<String, Object>> list = jt.queryForList("SHOW TABLES");
                for (Map<String, Object> row : list) {
                    Object t = row.values().iterator().next();
                    if (t != null) tables.add(String.valueOf(t));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 存缓存
        tableCache.put(type.toUpperCase(Locale.ROOT), new CacheEntry(tables));
        return tables;
    }

    /**
     * 获取指定表的所有列名（用于构造排序选项 / 防止 SQL 注入）
     *
     * @param type      数据库类型（MYSQL / DM8 / POSTGRESQL）
     * @param jt        对应的 JdbcTemplate
     * @param tableName 表名
     * @return 列名列表（统一为大写）
     */
    private List<String> listColumnsForTable(String type, JdbcTemplate jt, String tableName) {
        List<String> cols = new ArrayList<>();
        try {
            if ("DM8".equalsIgnoreCase(type)) {
                // DM8: 通过 ALL_TAB_COLUMNS 获取列信息
                String owner = jt.queryForObject("SELECT CURRENT_SCHEMA FROM DUAL", String.class);

                List<Map<String, Object>> rows = jt.queryForList(
                        "SELECT COLUMN_NAME FROM ALL_TAB_COLUMNS WHERE OWNER = ? AND TABLE_NAME = ?",
                        owner.toUpperCase(Locale.ROOT),
                        tableName.toUpperCase(Locale.ROOT)
                );

                for (Map<String, Object> r : rows) {
                    Object name = r.get("COLUMN_NAME");
                    if (name != null) cols.add(String.valueOf(name).toUpperCase(Locale.ROOT));
                }
            } else if ("POSTGRESQL".equalsIgnoreCase(type)) {
                // PostgreSQL: 查询 information_schema.columns
                List<Map<String, Object>> rows = jt.queryForList(
                        "SELECT column_name FROM information_schema.columns WHERE table_name = ?",
                        tableName
                );
                for (Map<String, Object> r : rows) {
                    Object name = r.get("column_name");
                    if (name != null) cols.add(String.valueOf(name).toUpperCase(Locale.ROOT));
                }
            } else {
                // MySQL: 直接 DESCRIBE
                List<Map<String, Object>> rows = jt.queryForList("DESCRIBE " + tableName);
                for (Map<String, Object> r : rows) {
                    Object name = r.get("Field");
                    if (name != null) cols.add(String.valueOf(name).toUpperCase(Locale.ROOT));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return cols;
    }


    /**
     * 根据类型选对应 JdbcTemplate
     */
    private JdbcTemplate pickJdbcTemplate(String typeRaw) {
        if (typeRaw == null) return null;
        String type = typeRaw.toUpperCase(Locale.ROOT);
        try {
            return switch (type) {
                case "MYSQL" -> ctx.getBean("mainJdbcTemplate", JdbcTemplate.class);
                case "DM8" ->
                        ctx.containsBean("dmJdbcTemplate") ? ctx.getBean("dmJdbcTemplate", JdbcTemplate.class) : null;
                case "SANDBOX" ->
                        ctx.containsBean("sandboxJdbcTemplate") ? ctx.getBean("sandboxJdbcTemplate", JdbcTemplate.class) : null;
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取指定数据源的表列表
     */
    @GetMapping("/{type}/tables")
    public List<Map<String, Object>> listTables(@PathVariable("type") String typeRaw) {
        String type = typeRaw.toUpperCase(Locale.ROOT);

        JdbcTemplate jt = switch (type) {
            case "MYSQL" ->
                    ctx.containsBean("mainJdbcTemplate") ? ctx.getBean("mainJdbcTemplate", JdbcTemplate.class) : null;
            case "DM8" -> ctx.containsBean("dmJdbcTemplate") ? ctx.getBean("dmJdbcTemplate", JdbcTemplate.class) : null;
            default -> null;
        };

        if (jt == null) {
            return List.of(Map.of("error", "DataSource not configured: " + type));
        }

        try {
            List<Map<String, Object>> tables = jt.queryForList("SHOW TABLES");
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> row : tables) {
                String tableName = row.values().iterator().next().toString();
                result.add(Map.of("tableName", tableName));
            }
            return result;
        } catch (Exception e) {
            // 针对 DM8 数据库使用不同 SQL
            if (type.equals("DM8")) {
                return jt.queryForList("SELECT TABLE_NAME FROM ALL_TABLES WHERE OWNER = 'SYSDBA'");
            }
            return List.of(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 获取指定表的字段信息
     */
    @GetMapping("/{type}/tables/{tableName}")
    public List<Map<String, Object>> describeTable(
            @PathVariable("type") String typeRaw,
            @PathVariable("tableName") String tableName
    ) {
        String type = typeRaw.toUpperCase(Locale.ROOT);

        JdbcTemplate jt = switch (type) {
            case "MYSQL" ->
                    ctx.containsBean("mainJdbcTemplate") ? ctx.getBean("mainJdbcTemplate", JdbcTemplate.class) : null;
            case "DM8" -> ctx.containsBean("dmJdbcTemplate") ? ctx.getBean("dmJdbcTemplate", JdbcTemplate.class) : null;
            default -> null;
        };

        if (jt == null) {
            return List.of(Map.of("error", "DataSource not configured: " + type));
        }

        try {
            if (type.equals("MYSQL")) {
                return jt.queryForList("DESCRIBE " + tableName);
            } else if (type.equals("DM8")) {

                String owner = jt.queryForObject(
                        "SELECT SYS_CONTEXT('USERENV','CURRENT_SCHEMA') FROM DUAL",
                        String.class
                );

                return jt.queryForList(
                        "SELECT COLUMN_NAME, DATA_TYPE, DATA_LENGTH FROM ALL_TAB_COLUMNS WHERE OWNER = ? AND TABLE_NAME = ?",
                        owner.toUpperCase(),
                        tableName.toUpperCase()
                );

            } else {
                return List.of(Map.of("error", "Unsupported database type: " + type));
            }
        } catch (Exception e) {
            return List.of(Map.of("error", e.getMessage()));
        }
    }

    // ✅ rows：去掉重复的 /api/datasources 前缀
    @GetMapping("/{type}/tables/{table}/rows")
    public Map<String, Object> previewRows(
            @PathVariable String type,
            @PathVariable String table,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String orderBy,
            @RequestParam(defaultValue = "ASC") String orderDir
    ) {
        JdbcTemplate jt = pickJdbcTemplate(type);
        if (jt == null) return Map.of("success", false, "message", "Unsupported datasource: " + type);

        // 白名单：只允许已知表
        Set<String> tableSet = listAllTableNamesCached(type, jt);
        if (!isSafeTableName(table, tableSet)) {
            return Map.of("success", false, "message", "Invalid table name");
        }

        // 列名（用于校验排序字段）
        List<String> columns = listColumnsForTable(type, jt, table);
        Set<String> columnSet = new HashSet<>(columns);
        String safeOrderBy = (orderBy != null && columnSet.contains(orderBy)) ? orderBy : null;
        String safeOrderDir = "DESC".equalsIgnoreCase(orderDir) ? "DESC" : "ASC";

        int pageNo = Math.max(page, 1);
        int pageSize = Math.max(Math.min(size, 200), 1);
        int offset = (pageNo - 1) * pageSize;

        String quoted = quoteFullTableName(type, table);

        Long total = 0L;
        try {
            total = jt.queryForObject("SELECT COUNT(*) FROM " + quoted, Long.class);
            if (total == null) total = 0L;
        } catch (Exception e) {
            return Map.of("success", false, "message", "Count failed: " + e.getMessage());
        }

        String sql;
        List<Map<String, Object>> rows;
        try {
            if ("DM8".equalsIgnoreCase(type)) {
                sql = "SELECT * FROM " + quoted
                        + (safeOrderBy != null ? " ORDER BY " + quoteIdent(type, safeOrderBy) + " " + safeOrderDir : "")
                        + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
                rows = jt.queryForList(sql, offset, pageSize);
            } else { // MySQL / PostgreSQL
                sql = "SELECT * FROM " + quoted
                        + (safeOrderBy != null ? " ORDER BY " + quoteIdent(type, safeOrderBy) + " " + safeOrderDir : "")
                        + " LIMIT ? OFFSET ?";
                rows = jt.queryForList(sql, pageSize, offset);
            }
        } catch (Exception e) {
            return Map.of("success", false, "message", "Query failed: " + e.getMessage());
        }

        List<String> cols = rows.isEmpty() ? columns : new ArrayList<>(rows.get(0).keySet());
        return Map.of(
                "success", true,
                "page", pageNo,
                "size", pageSize,
                "total", total,
                "columns", cols,
                "rows", rows
        );
    }

    // ✅ peek：同理去掉重复前缀
    @GetMapping("/{type}/tables/{table}/peek")
    public Map<String, Object> peekRows(
            @PathVariable String type,
            @PathVariable String table,
            @RequestParam(defaultValue = "50") int limit
    ) {
        JdbcTemplate jt = pickJdbcTemplate(type);
        if (jt == null) return Map.of("success", false, "message", "Unsupported datasource: " + type);

        Set<String> tableSet = listAllTableNamesCached(type, jt);
        if (!isSafeTableName(table, tableSet)) {
            return Map.of("success", false, "message", "Invalid table name");
        }

        String quoted = quoteFullTableName(type, table);
        int n = Math.max(Math.min(limit, 200), 1);

        try {
            String sql;
            List<Map<String, Object>> rows;
            if ("DM8".equalsIgnoreCase(type)) {
                sql = "SELECT * FROM " + quoted + " FETCH FIRST ? ROWS ONLY";
                rows = jt.queryForList(sql, n);
            } else {
                sql = "SELECT * FROM " + quoted + " LIMIT ?";
                rows = jt.queryForList(sql, n);
            }
            return Map.of("success", true, "rows", rows);
        } catch (Exception e) {
            return Map.of("success", false, "message", "Peek failed: " + e.getMessage());
        }
    }

    // ✅ 新增一个返回列名的接口，给排序下拉用
    @GetMapping("/{type}/tables/{table}/columns")
    public List<Map<String, Object>> columnsForOrder(@PathVariable String type,
                                                     @PathVariable String table) {
        JdbcTemplate jt = pickJdbcTemplate(type);
        if (jt == null) return List.of(Map.of("error", "Unsupported datasource: " + type));

        // 直接返回统一字段名：columnName / dataType / comment
        List<Map<String, Object>> rows = new ArrayList<>();
        try {
            if ("DM8".equalsIgnoreCase(type)) {
                var list = jt.queryForList("""
                            SELECT c.COLUMN_NAME, c.DATA_TYPE,
                                   COALESCE(cc.COMMENTS, '') AS COMMENTS
                            FROM ALL_TAB_COLUMNS c
                            LEFT JOIN ALL_COL_COMMENTS cc
                              ON cc.OWNER = c.OWNER
                             AND cc.TABLE_NAME = c.TABLE_NAME
                             AND cc.COLUMN_NAME = c.COLUMN_NAME
                            WHERE c.TABLE_NAME = ?
                        """, table.toUpperCase(Locale.ROOT));
                for (var r : list) {
                    rows.add(Map.of(
                            "columnName", r.get("COLUMN_NAME"),
                            "dataType", r.get("DATA_TYPE"),
                            "comment", r.getOrDefault("COMMENTS", "")
                    ));
                }
            } else { // MySQL
                var list = jt.queryForList("DESCRIBE " + table);
                for (var r : list) {
                    rows.add(Map.of(
                            "columnName", r.get("Field"),
                            "dataType", r.get("Type"),
                            "comment", ""
                    ));
                }
            }
        } catch (Exception e) {
            return List.of(Map.of("error", e.getMessage()));
        }
        return rows;
    }

    @GetMapping("/{type}/current-db")
    public Map<String, Object> currentDb(@PathVariable String type) {
        JdbcTemplate jt = pickJdbcTemplate(type);
        if (jt == null) {
            return Map.of("success", false, "message", "Unsupported datasource: " + type);
        }
        try {
            String db;
            switch (type.toUpperCase(Locale.ROOT)) {
                case "MYSQL" -> db = jt.queryForObject("SELECT DATABASE()", String.class);
                case "DM8" -> {
                    // 当前 schema；如果你要当前库名，可用 v$database 视图（有权限时）
                    db = jt.queryForObject("SELECT SYS_CONTEXT('USERENV','CURRENT_SCHEMA') FROM DUAL", String.class);
                }
                case "POSTGRESQL" -> db = jt.queryForObject("SELECT current_database()", String.class);
                default -> db = null;
            }
            return Map.of("success", true, "database", db);
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }


    // === 帮助方法（和你现有的工具方法放在一起即可）===
    private boolean isSafeTableName(String table, Set<String> known) {
        // 允许 schema.table 或 table，两段都校验在已知集合中（忽略大小写）
        String t = table == null ? "" : table.trim();
        if (t.isEmpty()) return false;
        if (known == null || known.isEmpty()) return false;
        // 统一大写比较
        java.util.function.Function<String, String> norm = s -> s == null ? "" : s.trim().toUpperCase();
        String tt = norm.apply(t);
        // known 里存 “SCHEMA.TABLE” 或 “TABLE”，你可以按你的 listTables 实现对齐
        return known.stream().map(norm).anyMatch(k -> k.equals(tt));
    }

    private String quoteIdent(String type, String ident) {
        // 按方言引用标识符，避免关键字冲突
        // MySQL/PostgreSQL/DM8 都支持双引号（PG/DM8严格，MySQL需开启 sql_mode=ANSI_QUOTES 才把 "x" 视作标识符）
        // 为兼容性：MySQL 用 `x`，PG/DM8 用 "x"
        // DM8 和 PostgreSQL 使用双引号
        if ("DM8".equalsIgnoreCase(type) || "POSTGRESQL".equalsIgnoreCase(type)) {
            return "\"" + ident.toUpperCase() + "\"";
        }

        // MySQL 用反引号
        return "`" + ident + "`";

    }

    private String quoteFullTableName(String type, String table) {
        // 支持 schema.table
        String[] parts = table.split("\\.");
        if (parts.length == 2) {
            return quoteIdent(type, parts[0]) + "." + quoteIdent(type, parts[1]);
        }
        return quoteIdent(type, table);
    }

}
