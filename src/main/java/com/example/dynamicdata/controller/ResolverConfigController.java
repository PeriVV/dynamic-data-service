package com.example.dynamicdata.controller;

import java.nio.charset.Charset;
import java.util.*;

import com.example.dynamicdata.utils.DbNameResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StreamUtils;

import com.example.dynamicdata.entity.ResolverConfig;
import com.example.dynamicdata.service.DynamicSqlExecutor;
import com.example.dynamicdata.service.ResolverConfigService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.charset.StandardCharsets;

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

    private String dataSourceKind;

    // 注入各数据源的 JdbcTemplate 和 URL（按需修改你的 Bean 名和配置键）
    @Autowired(required = false) JdbcTemplate mainJdbcTemplate;     // MySQL
    @Autowired(required = false) JdbcTemplate dmJdbcTemplate;        // DM8
    @Autowired(required = false) JdbcTemplate pgJdbcTemplate;        // PostgreSQL

    @Value("${spring.datasource.url:}")       private String mysqlUrl;
    @Value("${dm8.datasource.url:}")          private String dm8Url;
    @Value("${postgres.datasource.url:}")     private String pgUrl;

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
     * @param servletRequest 包含SQL语句和参数的请求对象
     * @return 查询结果或错误信息
     */
    @PostMapping(value = "/test-sql", consumes = MediaType.ALL_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> testSql(HttpServletRequest servletRequest) {
        Map<String, Object> response = new HashMap<>();

        try {
            // 兼容各种 Content-Type：手动读取 body，若是 JSON 则解析，否则用空对象
            Map<String, Object> request = new HashMap<>();
            try {
                String body = StreamUtils.copyToString(
                        servletRequest.getInputStream(),
                        servletRequest.getCharacterEncoding() != null
                                ? Charset.forName(servletRequest.getCharacterEncoding())
                                : StandardCharsets.UTF_8);
                if (body != null && !body.isBlank()) {
                    request = new ObjectMapper().readValue(body, Map.class);
                }
            } catch (Exception parseEx) {
                response.put("success", false);
                response.put("message", "请求体读取或解析失败，请确认已发送 JSON");
                return ResponseEntity.badRequest().body(response);
            }

            String sql = Optional.ofNullable(request.get("sql"))
                    .map(Object::toString)
                    .orElse("")
                    .trim();
            @SuppressWarnings("unchecked")
            Map<String, Object> parameters = request.get("parameters") instanceof Map ?
                    (Map<String, Object>) request.get("parameters") :
                    new HashMap<>();

            if (sql.isEmpty()) {
                response.put("success", false);
                response.put("message", "SQL query is required");
                return ResponseEntity.badRequest().body(response);
            }

            String dataSourceKind = Optional.ofNullable(request.get("dataSourceKind"))
                    .map(Object::toString)
                    .map(String::toUpperCase)
                    .orElse("MYSQL");

            String lowerSql = sql.trim().toLowerCase();
            boolean isSelect = lowerSql.startsWith("select");
            boolean useSandbox = !isSelect;

            if (isSelect) {
                List<Map<String, Object>> result =
                        sqlExecutor.executeQuery(sql, parameters, dataSourceKind, useSandbox);

                response.put("success", true);
                response.put("type", "QUERY");
                response.put("database", useSandbox ? "sandbox" : "main");
                response.put("dbName", sqlExecutor.currentDb(dataSourceKind, useSandbox));
                response.put("data", result);
                response.put("count", result.size());
            } else {
                int affected =
                        sqlExecutor.executeUpdate(sql, parameters, dataSourceKind, useSandbox);

                response.put("success", true);
                response.put("type", "UPDATE");
                response.put("database", useSandbox ? "sandbox" : "main");
                response.put("dbName", sqlExecutor.currentDb(dataSourceKind, useSandbox));
                response.put("affectedRows", affected);
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            // ★ 找到根本原因
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            response.put("success", false);
            response.put("message", "Error executing SQL: " + root.getMessage());
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

    // 把 resolver 实体转为 Map/DTO 时调用：
    private Map<String, Object> toView(ResolverConfig rc) {
        String type = (rc.getDataSource() == null ? "MYSQL" : rc.getDataSource()).toUpperCase(Locale.ROOT);

        String url;
        JdbcTemplate jt;
        switch (type) {
            case "DM8" -> { url = dm8Url; jt = dmJdbcTemplate; }
            case "POSTGRESQL" -> { url = pgUrl; jt = pgJdbcTemplate; }
            default -> { url = mysqlUrl; jt = mainJdbcTemplate; }
        }
        String dbName = DbNameResolver.resolveDatabaseName(type, url, jt);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rc.getId());
        m.put("resolverName", rc.getResolverName());
        m.put("operationType", rc.getOperationType());
        m.put("description", rc.getDescription());
        m.put("dataSource", type);
        m.put("databaseName", dbName);         // ★ 新增字段
        m.put("sqlQuery", rc.getSqlQuery());
        m.put("inputParameters", rc.getInputParameters());
        m.put("outputFields", rc.getOutputFields());
        m.put("enabled", rc.getEnabled());
        return m;
    }
}
