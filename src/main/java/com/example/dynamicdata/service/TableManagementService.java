package com.example.dynamicdata.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class TableManagementService {

    private final DynamicSqlExecutor sqlExecutor;
    private static final Pattern IDENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /**
     * =======================
     * 1. 创建表
     * payload 示例:
     * {
     * "tableName": "users",
     * "columns": [{
     * "name": "id",
     * "type": "INT",
     * "length": 11,
     * "nullable": false,
     * "comment": "主键"
     * }]
     * }
     * ========================
     */
    public Map<String, Object> createTable(String ds, String tableName, Map<String, Object> payload) {
        String dsKind = (ds == null ? "MYSQL" : ds.trim().toUpperCase());

        if (!isValidTableName(tableName)) {
            return fail("无效的表名");
        }

        List<Map<String, Object>> columns = (List<Map<String, Object>>) payload.get("columns");
        if (columns == null || columns.isEmpty()) {
            return fail("缺少字段定义 columns");
        }

        try {
            if ("DM8".equals(dsKind)) {
                // ===== DM8 风格：不使用反引号，不在列定义中写 COMMENT，用 COMMENT ON COLUMN =====
                StringBuilder create = new StringBuilder();
                create.append("CREATE TABLE ").append(quoteTable(dsKind, tableName)).append(" (\n");

                List<String> defs = new ArrayList<>();
                List<String> commentSqls = new ArrayList<>();

                for (Map<String, Object> col : columns) {
                    String name = (String) col.get("name");
                    if (!isValidIdentifier(name)) {
                        return fail("字段名不合法: " + name);
                    }
                    String type = sanitizeType(col.get("type"));
                    Integer len = (Integer) col.get("length");
                    Boolean nullable = (Boolean) col.get("nullable");
                    String comment = (String) col.get("comment");

                    if (name == null || name.isBlank()) continue;

                    // 简单的 DM8 类型映射
                    String upperType = (type == null ? "VARCHAR" : type.trim().toUpperCase());
                    String dmType;

                    if (upperType.startsWith("INT")) {
                        dmType = "INTEGER";
                    } else if (upperType.startsWith("BIGINT")) {
                        dmType = "BIGINT";
                    } else if (upperType.startsWith("DECIMAL") || upperType.startsWith("NUMBER")) {
                        // 需要可以进一步处理 p,s，这里仅处理长度
                        if (len != null && len > 0) {
                            dmType = "NUMBER(" + len + ")";
                        } else {
                            dmType = "NUMBER";
                        }
                    } else if (upperType.startsWith("VARCHAR")) {
                        int n = (len != null && len > 0) ? len : 255;
                        dmType = "VARCHAR(" + n + ")";
                    } else {
                        // 默认类型
                        dmType = "VARCHAR(255)";
                    }

                    StringBuilder one = new StringBuilder();
                    one.append(quoteIdent(dsKind, name)).append(" ").append(dmType);

                    if (nullable != null && !nullable) {
                        one.append(" NOT NULL");
                    }

                    defs.add(one.toString());

                    // COMMENT ON COLUMN 写法
                    if (comment != null && !comment.isBlank()) {
                        String safeComment = comment.replace("'", "''");
                        commentSqls.add("COMMENT ON COLUMN " + quoteTable(dsKind, tableName) + "." + quoteIdent(dsKind, name) +
                                " IS '" + safeComment + "'");
                    }
                }

                create.append("  ").append(String.join(",\n  ", defs)).append("\n)");
                String createSql = create.toString();

                // 执行 CREATE TABLE
                sqlExecutor.executeUpdate(createSql, Collections.emptyMap(), dsKind, false);

                // 执行 COMMENT ON COLUMN
                for (String csql : commentSqls) {
                    sqlExecutor.executeUpdate(csql, Collections.emptyMap(), dsKind, false);
                }

                return ok("DM8 表创建成功");
            } else {
                // ===== MySQL 风格 =====
                StringBuilder sb = new StringBuilder();
                sb.append("CREATE TABLE ").append(quoteTable(dsKind, tableName)).append(" (\n");

                List<String> defs = new ArrayList<>();
                for (Map<String, Object> col : columns) {
                    String name = (String) col.get("name");
                    if (!isValidIdentifier(name)) {
                        return fail("字段名不合法: " + name);
                    }
                    String type = sanitizeType(col.get("type"));
                    Integer len = (Integer) col.get("length");
                    Boolean nullable = (Boolean) col.get("nullable");
                    String comment = (String) col.get("comment");

                    if (name == null || name.isBlank()) continue;

                    StringBuilder one = new StringBuilder();
                    one.append(quoteIdent(dsKind, name)).append(" ");
                    one.append(type != null ? type : "VARCHAR");
                    if (len != null && len > 0) one.append("(").append(len).append(")");

                    if (nullable != null && !nullable) one.append(" NOT NULL");
                    else one.append(" NULL");

                    if (comment != null && !comment.isBlank()) {
                        one.append(" COMMENT '").append(comment.replace("'", "''")).append("'");
                    }

                    defs.add(one.toString());
                }

                sb.append("  ").append(String.join(",\n  ", defs)).append("\n);");
                String sql = sb.toString();

                sqlExecutor.executeUpdate(sql, Collections.emptyMap(), dsKind, false);
                return ok("MySQL 表创建成功");
            }
        } catch (Exception e) {
            return fail(e.getMessage());
        }
    }

    /**
     * =======================
     * 2. 删除表
     * ========================
     */
    public Map<String, Object> dropTable(String ds, String tableName) {
        String dsKind = (ds == null ? "MYSQL" : ds.trim().toUpperCase());
        if (!isValidTableName(tableName)) {
            return fail("无效的表名");
        }

        String sql = "DROP TABLE " + quoteTable(dsKind, tableName);
        try {
            sqlExecutor.executeUpdate(sql, new HashMap<>(), dsKind, false);
            return ok("表删除成功");
        } catch (Exception e) {
            return fail(e.getMessage());
        }
    }

    /**
     * 修改表结构（修改列类型/长度/注释等）
     */
    public Map<String, Object> alterTable(String ds, String tableName, Map<String, Object> payload) {
        String dsKind = (ds == null ? "MYSQL" : ds.trim().toUpperCase());

        List<Map<String, Object>> columns = (List<Map<String, Object>>) payload.get("columns");
        if (columns == null || columns.isEmpty()) {
            return fail("缺少字段定义 columns");
        }

        if (!isValidTableName(tableName)) {
            return fail("无效的表名");
        }

        List<String> alters = new ArrayList<>();

        for (Map<String, Object> col : columns) {
            String name = (String) col.get("name");
            if (!isValidIdentifier(name)) {
                return fail("字段名不合法: " + name);
            }
            String type = sanitizeType(col.get("type"));
            Integer len = (Integer) col.get("length");
            Boolean nullable = (Boolean) col.get("nullable");
            String comment = (String) col.get("comment");

            StringBuilder sb = new StringBuilder();
            sb.append("MODIFY COLUMN ").append(quoteIdent(dsKind, name)).append(" ").append(type);

            if (len != null && len > 0) sb.append("(").append(len).append(")");

            if (nullable != null && !nullable) sb.append(" NOT NULL");
            else sb.append(" NULL");

            if (comment != null && !comment.isBlank()) {
                sb.append(" COMMENT '").append(comment.replace("'", "''")).append("'");
            }

            alters.add(sb.toString());
        }

        String full = "ALTER TABLE " + quoteTable(dsKind, tableName) + "\n  " +
                String.join(",\n  ", alters);

        try {
            sqlExecutor.executeUpdate(full, new HashMap<>(), dsKind, false);
            return ok("表结构修改成功");
        } catch (Exception e) {
            return fail(e.getMessage());
        }
    }

    // --- 工具方法 ---
    private boolean isValidIdentifier(String name) {
        return name != null && IDENT.matcher(name).matches();
    }

    private boolean isValidTableName(String name) {
        if (name == null || name.isBlank()) return false;
        String[] parts = name.split("\\.");
        for (String p : parts) {
            if (!isValidIdentifier(p)) {
                return false;
            }
        }
        return true;
    }

    private String sanitizeType(Object typeObj) {
        if (typeObj == null) return "VARCHAR";
        String t = typeObj.toString().trim();
        // 仅允许字母/数字/下划线，过滤非法输入
        if (!t.matches("[A-Za-z0-9_]+")) {
            return "VARCHAR";
        }
        return t.toUpperCase();
    }

    private String quoteIdent(String dsKind, String ident) {
        String kind = dsKind == null ? "MYSQL" : dsKind.toUpperCase();
        if ("DM8".equals(kind) || "POSTGRESQL".equals(kind)) {
            return "\"" + ident + "\"";
        }
        return "`" + ident + "`";
    }

    private String quoteTable(String dsKind, String tableName) {
        String[] parts = tableName.split("\\.");
        java.util.List<String> quoted = new java.util.ArrayList<>();
        for (String p : parts) {
            quoted.add(quoteIdent(dsKind, p));
        }
        return String.join(".", quoted);
    }

    private Map<String, Object> ok(String msg) {
        return Map.of("success", true, "message", msg);
    }

    private Map<String, Object> fail(String msg) {
        return Map.of("success", false, "message", msg);
    }
}
