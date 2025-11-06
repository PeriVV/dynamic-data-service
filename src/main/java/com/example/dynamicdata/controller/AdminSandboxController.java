package com.example.dynamicdata.controller;

import com.example.dynamicdata.service.SandboxResetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理端：临时库重置为主库快照
 * 前端已对接：
 *   POST /api/admin/sandbox/reset
 */
@RestController
@RequestMapping("/api/admin/sandbox")
public class AdminSandboxController {

    private static final Logger log = LoggerFactory.getLogger(AdminSandboxController.class);
    private final SandboxResetService resetService;

    public AdminSandboxController(SandboxResetService resetService) {
        this.resetService = resetService;
    }

    /** 同步执行版本（与前端的 200/201 分支兼容） */
    @PostMapping("/reset")
    public ResponseEntity<?> resetSandboxNow(@RequestBody(required = false) Map<String, Object> body) {
        log.info("HTTP POST /api/admin/sandbox/reset triggered");

        try {
            Map<String, Object> result = resetService.resetSandboxSync();
            return ResponseEntity.ok(result); // 前端收到 200 -> 直接提示成功
        } catch (Exception e) {
            log.error("重置 sandbox 失败", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    // 如果你以后想做异步（返回 202 + jobId，前端已支持轮询），可扩展如下两个接口：
    // @PostMapping("/reset") -> 返回 202 + {jobId}
    // @GetMapping("/reset/{jobId}/status") -> 返回 {done:boolean, success:boolean, message:string}
}
