package com.example.dynamicdata.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TableManagementService {

    private final DynamicSqlExecutor sqlExecutor;

    /** =======================
     * 1. 创建表
     * payload:
     * {
     *   "tableName": "users",
     *   "columns": [{
     *       "name": "id",
     *       "type": "INT",
     *       "length": 11,
     *       "nullable": false,
     *       "comment": "主键"
     *   }]
     * }
     * ======================== */
    public Map<String, Object> createTable(String ds, String tableName, Map<String, Object> payload) {
        String dsKind = (ds == null ? "MYSQL" : ds.trim().toUpperCase());

        List<Map<String, Object>> columns = (List<Map<String, Object>>) payload.get("columns");
        if (columns == null || columns.isEmpty()) {
            return fail("缺少字段定义 columns");
        }

        try {
            if ("DM8".equals(dsKind)) {
                // ===== DM8 风格：去掉反引号 + 不用内联 COMMENT，用 COMMENT ON COLUMN =====
                StringBuilder create = new StringBuilder();
                create.append("CREATE TABLE ").append(tableName).append(" (\n");

                List<String> defs = new ArrayList<>();
                List<String> commentSqls = new ArrayList<>();

                for (Map<String, Object> col : columns) {
                    String name = (String) col.get("name");
                    String type = (String) col.get("type");
                    Integer len = (Integer) col.get("length");
                    Boolean nullable = (Boolean) col.get("nullable");
                    String comment = (String) col.get("comment");

                    if (name == null || name.isBlank()) continue;

                    // 简单做一个 DM8 的类型映射
                    String upperType = (type == null ? "VARCHAR" : type.trim().toUpperCase());
                    String dmType;

                    if (upperType.startsWith("INT")) {
                        dmType = "INTEGER";
                    } else if (upperType.startsWith("BIGINT")) {
                        dmType = "BIGINT";
                    } else if (upperType.startsWith("DECIMAL") || upperType.startsWith("NUMBER")) {
                        // 你有需要可以细分 p,s，这里先只用长度
                        if (len != null && len > 0) {
                            dmType = "NUMBER(" + len + ")";
                        } else {
                            dmType = "NUMBER";
                        }
                    } else if (upperType.startsWith("VARCHAR")) {
                        int n = (len != null && len > 0) ? len : 255;
                        dmType = "VARCHAR(" + n + ")";
                    } else {
                        // 默认给个 VARCHAR(255)
                        dmType = "VARCHAR(255)";
                    }

                    StringBuilder one = new StringBuilder();
                    // DM8 建议裸标识符或双引号，这里用裸的
                    one.append(name).append(" ").append(dmType);

                    if (nullable != null && !nullable) {
                        one.append(" NOT NULL");
                    }

                    defs.add(one.toString());

                    if (comment != null && !comment.isBlank()) {
                        String safeComment = comment.replace("'", "''");
                        commentSqls.add("COMMENT ON COLUMN " + tableName + "." + name +
                                " IS '" + safeComment + "'");
                    }
                }

                create.append("  ").append(String.join(",\n  ", defs)).append("\n)");
                String createSql = create.toString();

                // 执行 CREATE TABLE
                sqlExecutor.executeUpdate(createSql, Collections.emptyMap(), dsKind, false);

                // 执行各个 COMMENT ON COLUMN
                for (String csql : commentSqls) {
                    sqlExecutor.executeUpdate(csql, Collections.emptyMap(), dsKind, false);
                }

                return ok("DM8 表创建成功");
            } else {
                // ===== MySQL 风格（保留你原来的写法） =====
                StringBuilder sb = new StringBuilder();
                sb.append("CREATE TABLE ").append(tableName).append(" (\n");

                List<String> defs = new ArrayList<>();
                for (Map<String, Object> col : columns) {
                    String name = (String) col.get("name");
                    String type = (String) col.get("type");
                    Integer len = (Integer) col.get("length");
                    Boolean nullable = (Boolean) col.get("nullable");
                    String comment = (String) col.get("comment");

                    if (name == null || name.isBlank()) continue;

                    StringBuilder one = new StringBuilder();
                    one.append("`").append(name).append("` ");
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



    /** =======================
     * 2. 删除表
     * ======================== */
    public Map<String, Object> dropTable(String ds, String tableName) {
        String sql = "DROP TABLE " + tableName;
        try {
            sqlExecutor.executeUpdate(sql, new HashMap<>(), ds.toUpperCase(), false);
            return ok("表删除成功");
        } catch (Exception e) {
            return fail(e.getMessage());
        }
    }

    /** =======================
     * 3. 设计表：自动生成 ALTER TABLE
     * payload:
     * {
     *   "tableName": "users",
     *   "columns": [
     *      {"name":"id","type":"INT","length":11,"nullable":false,"comment":"主键"}
     *   ]
     * }
     * ======================== */
    public Map<String, Object> alterTable(String ds, String tableName, Map<String, Object> payload) {

        List<Map<String, Object>> columns = (List<Map<String, Object>>) payload.get("columns");
        if (columns == null || columns.isEmpty()) {
            return fail("缺少字段定义 columns");
        }

        List<String> alters = new ArrayList<>();

        for (Map<String, Object> col : columns) {
            String name = (String) col.get("name");
            String type = (String) col.get("type");
            Integer len = (Integer) col.get("length");
            Boolean nullable = (Boolean) col.get("nullable");
            String comment = (String) col.get("comment");

            StringBuilder sb = new StringBuilder();
            sb.append("MODIFY COLUMN `").append(name).append("` ").append(type);

            if (len != null && len > 0) sb.append("(").append(len).append(")");

            if (nullable != null && !nullable) sb.append(" NOT NULL");
            else sb.append(" NULL");

            if (comment != null && !comment.isBlank()) {
                sb.append(" COMMENT '").append(comment.replace("'", "''")).append("'");
            }

            alters.add(sb.toString());
        }

        String full = "ALTER TABLE " + tableName + "\n  " +
                String.join(",\n  ", alters);

        try {
            sqlExecutor.executeUpdate(full, new HashMap<>(), ds.toUpperCase(), false);
            return ok("表结构修改成功");
        } catch (Exception e) {
            return fail(e.getMessage());
        }
    }

    // --- Utils ---
    private Map<String, Object> ok(String msg) {
        return Map.of("success", true, "message", msg);
    }

    private Map<String, Object> fail(String msg) {
        return Map.of("success", false, "message", msg);
    }
}
