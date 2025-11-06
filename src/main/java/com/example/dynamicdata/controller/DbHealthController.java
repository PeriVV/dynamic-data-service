package com.example.dynamicdata.controller;

import com.example.dynamicdata.service.DynamicSqlExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DbHealthController {

    private final DynamicSqlExecutor exec;

    public DbHealthController(DynamicSqlExecutor exec) {
        this.exec = exec;
    }

    @GetMapping("/db/dm/ping")
    public String pingDm() {
        try {
            return exec.pingDm();  // "connected" / "unhealthy" / "disabled"
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }
}
