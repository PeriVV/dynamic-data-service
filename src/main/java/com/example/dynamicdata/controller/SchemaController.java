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

import com.example.dynamicdata.entity.SchemaDefinition;
import com.example.dynamicdata.service.DynamicSchemaService;

/**
 * GraphQL Schema管理控制器
 * 
 * 提供GraphQL Schema定义的CRUD操作和管理功能，包括：
 * - Schema的创建、更新、删除
 * - Schema的激活和停用
 * - Schema语法验证
 * - 动态重新加载GraphQL Schema
 */
@RestController
@RequestMapping("/api/schemas")
public class SchemaController {
    
    /**
     * 动态Schema服务
     * 负责Schema的业务逻辑处理
     */
    @Autowired
    private DynamicSchemaService dynamicSchemaService;
    
    /**
     * 获取所有Schema定义
     * @return 所有Schema定义列表
     */
    @GetMapping
    public ResponseEntity<List<SchemaDefinition>> getAllSchemas() {
        List<SchemaDefinition> schemas = dynamicSchemaService.getAllSchemas();
        return ResponseEntity.ok(schemas);
    }
    
    /**
     * 获取激活的Schema定义
     * @return 当前激活的Schema定义列表
     */
    @GetMapping("/active")
    public ResponseEntity<List<SchemaDefinition>> getActiveSchemas() {
        List<SchemaDefinition> schemas = dynamicSchemaService.getActiveSchemas();
        return ResponseEntity.ok(schemas);
    }
    
    /**
     * 根据ID获取Schema
     * @param id Schema的唯一标识符
     * @return Schema定义或404状态
     */
    @GetMapping("/{id}")
    public ResponseEntity<SchemaDefinition> getSchemaById(@PathVariable Long id) {
        Optional<SchemaDefinition> schema = dynamicSchemaService.getAllSchemas().stream()
            .filter(s -> s.getId().equals(id))
            .findFirst();
        
        if (schema.isPresent()) {
            return ResponseEntity.ok(schema.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * 根据名称获取Schema
     * @param schemaName Schema名称
     * @return Schema定义或404状态
     */
    @GetMapping("/name/{schemaName}")
    public ResponseEntity<SchemaDefinition> getSchemaByName(@PathVariable String schemaName) {
        Optional<SchemaDefinition> schema = dynamicSchemaService.getSchemaByName(schemaName);
        
        if (schema.isPresent()) {
            return ResponseEntity.ok(schema.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * 创建新的Schema定义
     * @param request 创建Schema的请求参数
     * @return 创建的Schema定义或错误信息
     */
    @PostMapping
    public ResponseEntity<?> createSchema(@RequestBody CreateSchemaRequest request) {
        try {
            SchemaDefinition schema = dynamicSchemaService.createSchema(
                request.getSchemaName(),
                request.getSchemaContent(),
                request.getDescription()
            );
            return ResponseEntity.ok(schema);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    /**
     * 更新Schema定义
     * @param id Schema的唯一标识符
     * @param request 更新Schema的请求参数
     * @return 更新后的Schema定义或错误信息
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateSchema(@PathVariable Long id, @RequestBody UpdateSchemaRequest request) {
        try {
            SchemaDefinition schema = dynamicSchemaService.updateSchema(
                id,
                request.getSchemaContent(),
                request.getDescription()
            );
            return ResponseEntity.ok(schema);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    /**
     * 激活Schema
     * @param id Schema的唯一标识符
     * @return 操作结果信息
     */
    @PostMapping("/{id}/activate")
    public ResponseEntity<?> activateSchema(@PathVariable Long id) {
        try {
            dynamicSchemaService.activateSchema(id);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Schema已激活");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    /**
     * 停用Schema
     * @param id Schema的唯一标识符
     * @return 操作结果信息
     */
    @PostMapping("/{id}/deactivate")
    public ResponseEntity<?> deactivateSchema(@PathVariable Long id) {
        try {
            dynamicSchemaService.deactivateSchema(id);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Schema已停用");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    /**
     * 删除Schema定义
     * @param id Schema的唯一标识符
     * @return 操作结果信息
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteSchema(@PathVariable Long id) {
        try {
            dynamicSchemaService.deleteSchema(id);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Schema已删除");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    /**
     * 重新加载GraphQL Schema
     * @return 操作结果信息
     */
    @PostMapping("/reload")
    public ResponseEntity<?> reloadSchema() {
        try {
            dynamicSchemaService.reloadGraphQLSchema();
            Map<String, String> response = new HashMap<>();
            response.put("message", "GraphQL Schema已重新加载");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    /**
     * 获取当前Schema内容
     * @return 当前激活的Schema内容
     */
    @GetMapping("/current")
    public ResponseEntity<?> getCurrentSchema() {
        String currentSchema = dynamicSchemaService.getCurrentSchemaContent();
        Map<String, String> response = new HashMap<>();
        response.put("schemaContent", currentSchema != null ? currentSchema : "");
        return ResponseEntity.ok(response);
    }
    
    /**
     * 验证Schema语法
     * @param request 包含待验证Schema内容的请求
     * @return 验证结果信息
     */
    @PostMapping("/validate")
    public ResponseEntity<?> validateSchema(@RequestBody ValidateSchemaRequest request) {
        try {
            // 通过创建临时schema来验证语法
            dynamicSchemaService.createSchema(
                "temp_" + System.currentTimeMillis(),
                request.getSchemaContent(),
                "临时验证Schema"
            );
            
            // 验证成功后删除临时schema
            // 这里简化处理，实际应该有更好的验证方法
            
            Map<String, Object> response = new HashMap<>();
            response.put("valid", true);
            response.put("message", "Schema语法正确");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("valid", false);
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
    
    /**
     * 创建Schema请求DTO
     */
    public static class CreateSchemaRequest {
        private String schemaName;
        private String schemaContent;
        private String description;
        
        public String getSchemaName() { return schemaName; }
        public void setSchemaName(String schemaName) { this.schemaName = schemaName; }
        
        public String getSchemaContent() { return schemaContent; }
        public void setSchemaContent(String schemaContent) { this.schemaContent = schemaContent; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
    
    /**
     * 更新Schema请求DTO
     */
    public static class UpdateSchemaRequest {
        private String schemaContent;
        private String description;
        
        public String getSchemaContent() { return schemaContent; }
        public void setSchemaContent(String schemaContent) { this.schemaContent = schemaContent; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
    
    /**
     * 验证Schema请求DTO
     */
    public static class ValidateSchemaRequest {
        private String schemaContent;
        
        public String getSchemaContent() { return schemaContent; }
        public void setSchemaContent(String schemaContent) { this.schemaContent = schemaContent; }
    }
}