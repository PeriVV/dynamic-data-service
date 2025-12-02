package com.example.dynamicdata.controller;

import com.example.dynamicdata.service.TableManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/datasources/{ds}/tables")
@RequiredArgsConstructor
public class TableManagementController {

    private final TableManagementService service;

    /** 创建表 */
    @PostMapping
    public Map<String, Object> createTable(
            @PathVariable("ds") String ds,
            @RequestBody Map<String, Object> payload
    ) {
        String tableName = (String) payload.get("tableName");
        return service.createTable(ds, tableName, payload);
    }

    /** 删除表 */
    @DeleteMapping("/{tableName}")
    public Map<String, Object> dropTable(
            @PathVariable("ds") String ds,
            @PathVariable("tableName") String tableName
    ) {
        return service.dropTable(ds, tableName);
    }

    /** 修改字段（设计表） */
    @PutMapping("/{tableName}")
    public Map<String, Object> alterTable(
            @PathVariable("ds") String ds,
            @PathVariable("tableName") String tableName,
            @RequestBody Map<String, Object> payload
    ) {
        return service.alterTable(ds, tableName, payload);
    }
}
