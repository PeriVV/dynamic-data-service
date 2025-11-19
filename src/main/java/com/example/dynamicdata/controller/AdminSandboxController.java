package com.example.dynamicdata.controller;

import com.example.dynamicdata.service.SandboxResetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端：临时库重置为主库快照
 * 前端已对接：
 *   POST /api/admin/sandbox/reset   → MySQL 测试库重置
 *   POST /api/admin/sandbox/reset-dm8 → DM8 测试库重置
 */
@RestController
@RequestMapping("/api/admin/sandbox")
public class AdminSandboxController {

    private static final Logger log = LoggerFactory.getLogger(AdminSandboxController.class);

    private final SandboxResetService resetService;

    // 新增：注入 DM8 主库 + DM8 测试库
    private final JdbcTemplate dmJdbcTemplate;         // DM8 主库
    private final JdbcTemplate dmSandboxJdbcTemplate;  // DM8 测试库

    public AdminSandboxController(
            SandboxResetService resetService,
            @Qualifier("dmJdbcTemplate") JdbcTemplate dmJdbcTemplate,
            @Qualifier("dmSandboxJdbcTemplate") JdbcTemplate dmSandboxJdbcTemplate
    ) {
        this.resetService = resetService;
        this.dmJdbcTemplate = dmJdbcTemplate;
        this.dmSandboxJdbcTemplate = dmSandboxJdbcTemplate;
    }

    /** ============================
     * ① MySQL 测试库重置（原来的功能）
     * ============================ */
    @PostMapping("/reset")
    public ResponseEntity<?> resetSandboxNow(@RequestBody(required = false) Map<String, Object> body) {
        log.info("HTTP POST /api/admin/sandbox/reset (MySQL) triggered");

        try {
            Map<String, Object> result = resetService.resetSandboxSync();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("重置 MySQL sandbox 失败", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }


    /** ============================
     * ② DM8 测试库重置（新增）
     * URL：POST /api/admin/sandbox/reset-dm8
     * 前端自动根据 dataSource == DM8 调用这个接口
     * ============================ */
    @PostMapping("/reset-dm8")
    public ResponseEntity<?> resetDm8Sandbox(@RequestBody(required = false) Map<String, Object> body) {
        log.info("HTTP POST /api/admin/sandbox/reset-dm8 (DM8) triggered");

        try {
            // 1. 从 DM8 主库 SYSDBA 读取所有订单
            List<Map<String, Object>> rows = dmJdbcTemplate.queryForList(
                    "SELECT ID, USER_NAME, AMOUNT, CREATED_AT FROM SYSDBA.ORDERS ORDER BY ID"
            );

            // 2. 清空 DM8 测试库 TESTDB.ORDERS
            dmSandboxJdbcTemplate.update("TRUNCATE TABLE TESTDB.ORDERS");

            // 3. 逐条写入测试库 TESTDB
            for (var r : rows) {
                dmSandboxJdbcTemplate.update(
                        "INSERT INTO TESTDB.ORDERS (ID, USER_NAME, AMOUNT, CREATED_AT) VALUES (?,?,?,?)",
                        r.get("ID"),
                        r.get("USER_NAME"),
                        r.get("AMOUNT"),
                        r.get("CREATED_AT")
                );
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "DM8 测试库已重置：SYSDBA → TESTDB（共 " + rows.size() + " 条记录）"
            ));

        } catch (Exception e) {
            log.error("重置 DM8 sandbox 失败", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "重置 DM8 测试库失败: " + e.getMessage()
            ));
        }
    }

}
