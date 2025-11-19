package com.example.dynamicdata.utils;

// src/main/java/com/example/dynamicdata/util/DbNameResolver.java

import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DbNameResolver {

    // mysql: jdbc:mysql://host:3306/dbname?params
    private static final Pattern MYSQL_DB = Pattern.compile("^jdbc:mysql://[^/]+/([^?]+).*$", Pattern.CASE_INSENSITIVE);
    // dm8:   jdbc:dm://host:5236/DBNAME?SCHEMA=SYSDBA&...
    private static final Pattern DM8_DB  = Pattern.compile("^jdbc:dm://[^/]+/([^?]+)?.*$", Pattern.CASE_INSENSITIVE);

    /** 优先用 SQL 查询实时获取；失败再退化到解析 JDBC URL */
    public static String resolveDatabaseName(String type, String jdbcUrl, JdbcTemplate jt) {
        type = type == null ? "" : type.toUpperCase(Locale.ROOT);
        try {
            if ("MYSQL".equals(type)) {
                // MySQL: 当前库名
                String db = jt.queryForObject("SELECT DATABASE()", String.class);
                if (db != null && !db.isBlank()) return db;
            } else if ("POSTGRESQL".equals(type)) {
                // PG: 当前数据库
                String db = jt.queryForObject("SELECT current_database()", String.class);
                if (db != null && !db.isBlank()) return db;
            } else if ("DM8".equals(type)) {
                // DM8: 当前 schema（最有用的信息一般是 schema）
                String schema = jt.queryForObject("SELECT SYS_CONTEXT('USERENV','CURRENT_SCHEMA') FROM dual", String.class);
                if (schema != null && !schema.isBlank()) return schema;
            }
        } catch (Exception ignore) {
            // ignore and fallback to URL parsing
        }
        return parseFromUrl(type, jdbcUrl);
    }

    public static String parseFromUrl(String type, String jdbcUrl) {
        if (jdbcUrl == null) return "-";
        type = type == null ? "" : type.toUpperCase(Locale.ROOT);
        if ("MYSQL".equals(type) || "POSTGRESQL".equals(type)) {
            Matcher m = MYSQL_DB.matcher(jdbcUrl);
            if (m.matches()) return m.group(1);
        } else if ("DM8".equals(type)) {
            // 1) 如果 URL 含 /DBNAME 就取之；2) 否则从 SCHEMA= 取；都没有就返回 DMSERVER
            Matcher m = DM8_DB.matcher(jdbcUrl);
            if (m.matches() && m.group(1) != null) return m.group(1);
            int i = jdbcUrl.toUpperCase(Locale.ROOT).indexOf("SCHEMA=");
            if (i >= 0) {
                String s = jdbcUrl.substring(i + 7);
                int amp = s.indexOf('&');
                return amp > 0 ? s.substring(0, amp) : s;
            }
            return "DMSERVER";
        }
        // 最后兜底：尝试 URI path
        try {
            URI u = new URI(jdbcUrl.replace("jdbc:", ""));
            String path = u.getPath();
            if (path != null && path.length() > 1) return path.substring(1);
        } catch (URISyntaxException ignored) {}
        return "-";
    }
}
