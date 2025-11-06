package com.example.dynamicdata.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 将临时库（sandbox）重置为主库（main）的快照。
 * 要求 mainDb 与 sandboxDb 在同一 MySQL 实例下（URL 仅库名不同）。
 */
@Service
public class SandboxResetService {

    private static final Logger log = LoggerFactory.getLogger(SandboxResetService.class);

    private final JdbcTemplate mainJdbc;
    private final JdbcTemplate sandboxJdbc;

    public SandboxResetService(@Qualifier("mainJdbcTemplate") JdbcTemplate mainJdbc,
                               @Qualifier("sandboxJdbcTemplate") JdbcTemplate sandboxJdbc) {
        this.mainJdbc = mainJdbc;
        this.sandboxJdbc = sandboxJdbc;
    }

    /** 对外同步入口：同步结构+数据，返回统计信息 */
    public Map<String, Object> resetSandboxSync() {
        String mainDb = currentDb(mainJdbc);
        String sandboxDb = currentDb(sandboxJdbc);
        if (mainDb == null || sandboxDb == null) {
            throw new IllegalStateException("无法获取当前数据库名，请检查数据源配置");
        }
        if (mainDb.equalsIgnoreCase(sandboxDb)) {
            throw new IllegalStateException("主库与临时库相同，禁止重置！");
        }

        log.info("Reset sandbox DB: {} -> {}", mainDb, sandboxDb);

        // 1) 读主库所有“物理表”
        List<String> mainTables = listTables(mainDb, false);
        // 也可选择同步视图：listTables(mainDb, true) 获取视图
        // 这里仅同步“BASE TABLE”，视图可按需扩展

        // 2) 在 sandbox 连接上，删除 sandbox 的所有表
        disableFkChecks(sandboxJdbc);
        try {
            List<String> sandboxTables = listTables(sandboxDb, false);
            dropTables(sandboxDb, sandboxTables);

            // 3) 逐表复制结构与数据
            Map<String, Integer> rowCount = new LinkedHashMap<>();
            for (String table : mainTables) {
                createLikeFromMain(sandboxDb, mainDb, table);
                int n = copyDataFromMain(sandboxDb, mainDb, table);
                rowCount.put(table, n);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", String.format("已将主库 %s 的 %d 张表同步至临时库 %s", mainDb, mainTables.size(), sandboxDb));
            result.put("mainDb", mainDb);
            result.put("sandboxDb", sandboxDb);
            result.put("tables", mainTables);
            result.put("rowsByTable", rowCount);
            result.put("totalRows", rowCount.values().stream().mapToInt(Integer::intValue).sum());
            return result;
        } finally {
            enableFkChecks(sandboxJdbc);
        }
    }

    private String currentDb(JdbcTemplate jt) {
        try {
            return jt.queryForObject("SELECT DATABASE()", String.class);
        } catch (Exception e) {
            log.warn("SELECT DATABASE() 失败: {}", e.getMessage());
            return null;
        }
    }

    /** 列出某库中的表或视图 */
    private List<String> listTables(String dbName, boolean views) {
        // SHOW FULL TABLES IN db WHERE Table_type='BASE TABLE' / 'VIEW'
        String sql = "SHOW FULL TABLES IN `" + dbName + "` WHERE Table_type = ?";
        String type = views ? "VIEW" : "BASE TABLE";
        return sandboxJdbc.query(sql, rs -> {
            List<String> list = new ArrayList<>();
            while (rs.next()) {
                // 返回两列：表名 + 类型，这里取第一列
                list.add(rs.getString(1));
            }
            return list;
        }, type);
    }

    private void disableFkChecks(JdbcTemplate jt) {
        jt.execute("SET FOREIGN_KEY_CHECKS=0");
    }

    private void enableFkChecks(JdbcTemplate jt) {
        jt.execute("SET FOREIGN_KEY_CHECKS=1");
    }

    private void dropTables(String sandboxDb, List<String> tables) {
        if (tables == null || tables.isEmpty()) return;
        // 先按外键拓扑排序更好；但我们已关闭 FK 检查，可直接删
        for (String t : tables) {
            String sql = "DROP TABLE IF EXISTS `" + sandboxDb + "`.`" + t + "`";
            log.debug("DROP: {}", sql);
            sandboxJdbc.execute(sql);
        }
    }

    private void createLikeFromMain(String sandboxDb, String mainDb, String table) {
        String sql = "CREATE TABLE `" + sandboxDb + "`.`" + table + "` LIKE `" + mainDb + "`.`" + table + "`";
        log.debug("CREATE LIKE: {}", sql);
        sandboxJdbc.execute(sql);
    }

    private int copyDataFromMain(String sandboxDb, String mainDb, String table) {
        String sql = "INSERT INTO `" + sandboxDb + "`.`" + table + "` SELECT * FROM `" + mainDb + "`.`" + table + "`";
        log.debug("COPY DATA: {}", sql);
        return sandboxJdbc.update(sql);
    }

    // —— 如需同步视图，可追加：SHOW CREATE VIEW + 替换 DEFINER + 在 sandbox 执行 CREATE VIEW —— //
}
