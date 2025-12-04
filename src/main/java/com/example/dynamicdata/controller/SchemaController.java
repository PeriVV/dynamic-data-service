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
 * GraphQL Schema 管理控制器
 *
 * 提供 GraphQL Schema 的 CRUD 操作和管理功能，包括：
 * - Schema 的创建、更新、删除
 * - Schema 的激活与停用
 * - Schema 语法验证
 * - 动态重新加载 GraphQL Schema
 */
@RestController
@RequestMapping("/api/schemas")
public class SchemaController {

    /**
     * 动态 Schema 服务，负责 Schema 的业务逻辑处理
     */
    @Autowired
    private DynamicSchemaService dynamicSchemaService;

    /**
     * 获取所有 Schema 定义
     * @return 所有 Schema 列表
     */
    @GetMapping
    public ResponseEntity<List<SchemaDefinition>> getAllSchemas() {
        List<SchemaDefinition> schemas = dynamicSchemaService.getAllSchemas();
        return ResponseEntity.ok(schemas);
    }

    /**
     * 获取当前激活的 Schema 定义
     * @return 激活状态的 Schema 列表
     */
    @GetMapping("/active")
    public ResponseEntity<List<SchemaDefinition>> getActiveSchemas() {
        List<SchemaDefinition> schemas = dynamicSchemaService.getActiveSchemas();
        return ResponseEntity.ok(schemas);
    }

    /**
     * 根据 ID 获取 Schema
     * @param id Schema 的唯一标识
     * @return Schema 定义或 404
     */
    @GetMapping("/{id}")
    public ResponseEntity<SchemaDefinition> getSchemaById(@PathVariable Long id) {
        Optional<SchemaDefinition> schema = dynamicSchemaService.getSchemaById(id);

        return schema.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 根据名称获取 Schema
     * @param schemaName Schema 名称
     * @return Schema 定义或 404
     */
    @GetMapping("/name/{schemaName}")
    public ResponseEntity<SchemaDefinition> getSchemaByName(@PathVariable String schemaName) {
        Optional<SchemaDefinition> schema = dynamicSchemaService.getSchemaByName(schemaName);

        return schema.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 创建新的 Schema 定义
     * @param request 创建 Schema 的请求参数
     * @return 创建成功的 Schema 或错误信息
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
     * 更新 Schema 定义
     * @param id Schema 的唯一标识
     * @param request 更新内容
     * @return 更新后的 Schema 或错误信息
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
     * 激活 Schema
     * @param id Schema 的唯一标识
     * @return 操作结果
     */
    @PostMapping("/{id}/activate")
    public ResponseEntity<?> activateSchema(@PathVariable Long id) {
        try {
            dynamicSchemaService.activateSchema(id);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Schema 已激活");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 停用 Schema
     * @param id Schema 的唯一标识
     * @return 操作结果
     */
    @PostMapping("/{id}/deactivate")
    public ResponseEntity<?> deactivateSchema(@PathVariable Long id) {
        try {
            dynamicSchemaService.deactivateSchema(id);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Schema 已停用");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 删除 Schema 定义
     * @param id Schema 的唯一标识
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteSchema(@PathVariable Long id) {
        try {
            dynamicSchemaService.deleteSchema(id);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Schema 已删除");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 重新加载 GraphQL Schema
     * @return 操作结果
     */
    @PostMapping("/reload")
    public ResponseEntity<?> reloadSchema() {
        try {
            dynamicSchemaService.reloadGraphQLSchema();
            Map<String, String> response = new HashMap<>();
            response.put("message", "GraphQL Schema 已重新加载");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 获取当前激活的 Schema 内容
     * @return 当前 Schema 文本内容
     */
    @GetMapping("/current")
    public ResponseEntity<?> getCurrentSchema() {
        String currentSchema = dynamicSchemaService.getCurrentSchemaContent();
        Map<String, String> response = new HashMap<>();
        response.put("schemaContent", currentSchema != null ? currentSchema : "");
        return ResponseEntity.ok(response);
    }

    /**
     * 验证 Schema 语法
     * @param request 包含 Schema 内容的请求
     * @return 验证结果
     */
    @PostMapping("/validate")
    public ResponseEntity<?> validateSchema(@RequestBody ValidateSchemaRequest request) {
        try {
            dynamicSchemaService.validateSchema(request.getSchemaContent());

            Map<String, Object> response = new HashMap<>();
            response.put("valid", true);
            response.put("message", "Schema 语法正确");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("valid", false);
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    // ================================
    // DTO 定义
    // ================================

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
     * 更新 Schema 请求 DTO
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
     * 验证 Schema 请求 DTO
     */
    public static class ValidateSchemaRequest {
        private String schemaContent;

        public String getSchemaContent() { return schemaContent; }
        public void setSchemaContent(String schemaContent) { this.schemaContent = schemaContent; }
    }
}
