package com.example.dynamicdata.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.dynamicdata.entity.ResolverConfig;
import com.example.dynamicdata.service.DynamicSqlExecutor;
import com.example.dynamicdata.service.ResolverConfigService;

/**
 * GraphQL解析器配置管理控制器
 * 
 * 提供解析器配置的CRUD操作和管理功能，包括：
 * - 解析器配置的创建、更新、删除
 * - 按ID、名称、操作类型查询配置
 * - SQL查询测试功能
 */
@RestController
@RequestMapping("/api/resolver-config")
public class ResolverConfigController {

    /**
     * 解析器配置服务
     * 负责解析器配置的业务逻辑处理
     */
    @Autowired
    private ResolverConfigService configService;

    /**
     * 动态SQL执行器
     * 负责SQL查询的执行和验证
     */
    @Autowired
    private DynamicSqlExecutor sqlExecutor;

    /**
     * 获取所有解析器配置
     * @return 所有解析器配置列表
     */
    @GetMapping
    public ResponseEntity<List<ResolverConfig>> getAllConfigs() {
        return ResponseEntity.ok(configService.getAllConfigs());
    }

    /**
     * 根据ID获取解析器配置
     * @param id 配置的唯一标识符
     * @return 解析器配置或404状态
     */
    @GetMapping("/{id}")
    public ResponseEntity<ResolverConfig> getConfigById(@PathVariable Long id) {
        Optional<ResolverConfig> config = configService.getConfigById(id);
        return config.map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 根据解析器名称获取配置
     * @param resolverName 解析器名称
     * @return 解析器配置或404状态
     */
    @GetMapping("/by-name/{resolverName}")
    public ResponseEntity<ResolverConfig> getConfigByResolverName(@PathVariable String resolverName) {
        Optional<ResolverConfig> config = configService.getConfigByResolverName(resolverName);
        return config.map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 根据操作类型获取解析器配置列表
     * @param operationType 操作类型（QUERY或MUTATION）
     * @return 指定类型的解析器配置列表
     */
    @GetMapping("/by-type/{operationType}")
    public ResponseEntity<List<ResolverConfig>> getConfigsByOperationType(@PathVariable String operationType) {
        return ResponseEntity.ok(configService.getConfigsByOperationType(operationType));
    }

    /**
     * 创建新的解析器配置
     * @param config 解析器配置对象
     * @return 创建结果和配置信息
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createConfig(@RequestBody ResolverConfig config) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (configService.existsByResolverName(config.getResolverName())) {
                response.put("success", false);
                response.put("message", "Resolver name already exists");
                return ResponseEntity.badRequest().body(response);
            }

            if (!isValidOperationType(config.getOperationType())) {
                response.put("success", false);
                response.put("message", "Invalid operation type. Must be QUERY or MUTATION");
                return ResponseEntity.badRequest().body(response);
            }

            ResolverConfig savedConfig = configService.saveConfig(config);
            response.put("success", true);
            response.put("config", savedConfig);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error creating config: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 更新解析器配置
     * @param id 配置的唯一标识符
     * @param config 更新的配置信息
     * @return 更新结果和配置信息
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateConfig(@PathVariable Long id, @RequestBody ResolverConfig config) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<ResolverConfig> existingConfigOpt = configService.getConfigById(id);
            if (existingConfigOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "Config not found");
                return ResponseEntity.notFound().build();
            }

            ResolverConfig existingConfig = existingConfigOpt.get();
            
            if (!existingConfig.getResolverName().equals(config.getResolverName()) 
                && configService.existsByResolverName(config.getResolverName())) {
                response.put("success", false);
                response.put("message", "Resolver name already exists");
                return ResponseEntity.badRequest().body(response);
            }

            if (!isValidOperationType(config.getOperationType())) {
                response.put("success", false);
                response.put("message", "Invalid operation type. Must be QUERY or MUTATION");
                return ResponseEntity.badRequest().body(response);
            }

            config.setId(id);
            ResolverConfig updatedConfig = configService.saveConfig(config);
            response.put("success", true);
            response.put("config", updatedConfig);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error updating config: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 删除解析器配置
     * @param id 配置的唯一标识符
     * @return 删除结果信息
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteConfig(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (configService.getConfigById(id).isEmpty()) {
                response.put("success", false);
                response.put("message", "Config not found");
                return ResponseEntity.notFound().build();
            }

            configService.deleteConfig(id);
            response.put("success", true);
            response.put("message", "Config deleted successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error deleting config: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 测试SQL查询
     * @param request 包含SQL语句和参数的请求对象
     * @return 查询结果或错误信息
     */
    @PostMapping("/test-sql")
    public ResponseEntity<Map<String, Object>> testSql(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();

        try {
            String sql = (String) request.get("sql");
            @SuppressWarnings("unchecked")
            Map<String, Object> parameters = request.get("parameters") != null ?
                    (Map<String, Object>) request.get("parameters") :
                    new HashMap<>();

            if (sql == null || sql.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "SQL query is required");
                return ResponseEntity.badRequest().body(response);
            }

            // 判断是否是查询语句
            String lowerSql = sql.trim().toLowerCase();
            boolean isSelect = lowerSql.startsWith("select");

            // 所有“查询”操作 → 主库
            // 所有“插入/更新/删除”操作 → 临时库
            boolean useSandbox = !isSelect;

            if (isSelect) {
                List<Map<String, Object>> result = sqlExecutor.executeQuery(sql, parameters, useSandbox);
                response.put("success", true);
                response.put("type", "QUERY");
                response.put("database", useSandbox ? "sandbox" : "main");
                response.put("dbName", sqlExecutor.currentDb(useSandbox));   // ★ 新增
                response.put("data", result);
                response.put("count", result.size());
            } else {
                int affected = sqlExecutor.executeUpdate(sql, parameters, useSandbox);
                response.put("success", true);
                response.put("type", "UPDATE");
                response.put("database", useSandbox ? "sandbox" : "main");
                response.put("dbName", sqlExecutor.currentDb(useSandbox));   // ★ 新增
                response.put("affectedRows", affected);
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error executing SQL: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }





    /**
     * 验证操作类型是否有效
     * @param operationType 操作类型
     * @return 是否为有效的操作类型（QUERY或MUTATION）
     */
    private boolean isValidOperationType(String operationType) {
        return "QUERY".equals(operationType) || "MUTATION".equals(operationType);
    }
}