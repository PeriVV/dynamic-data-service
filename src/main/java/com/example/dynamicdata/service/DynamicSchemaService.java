package com.example.dynamicdata.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.dynamicdata.entity.SchemaDefinition;
import com.example.dynamicdata.repository.SchemaDefinitionRepository;

import graphql.GraphQL;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;

/**
 * 动态GraphQL Schema管理服务类
 * 
 * 该服务类负责GraphQL Schema的完整生命周期管理，主要功能包括：
 * - Schema定义的增删改查操作
 * - Schema版本控制和激活状态管理
 * - 动态Schema重新加载和热更新
 * - Schema语法验证和错误处理
 * 
 * 核心特性：
 * 1. 多Schema支持：支持同时管理多个Schema定义
 * 2. 版本控制：每次更新自动递增版本号
 * 3. 激活管理：支持Schema的激活/停用切换
 * 4. 动态加载：运行时动态重新加载Schema配置
 * 5. 语法验证：创建和更新时进行Schema语法检查
 * 
 * 使用场景：
 * - 开发环境中频繁的Schema调试和测试
 * - 生产环境中的Schema版本升级
 * - 多租户系统中不同租户的Schema管理
 * 
 * @author Dynamic Data Service Team
 * @version 1.0
 * @since 2024-01-15
 */
@Service
@Transactional
public class DynamicSchemaService {
    
    /**
     * 日志记录器
     * 用于记录Schema管理过程中的操作日志和错误信息
     */
    private static final Logger logger = LoggerFactory.getLogger(DynamicSchemaService.class);
    
    /**
     * Schema定义数据访问层
     * 通过Spring依赖注入自动装配，用于Schema定义的持久化操作
     */
    @Autowired
    private SchemaDefinitionRepository schemaRepository;
    
    /**
     * 解析器配置服务
     * 用于获取GraphQL解析器配置，构建RuntimeWiring
     */
    @Autowired
    private ResolverConfigService resolverConfigService;
    
    /**
     * 动态SQL执行器
     * 用于执行SQL查询和更新操作
     */
    @Autowired
    private DynamicSqlExecutor sqlExecutor;
    
    /**
     * 当前激活的GraphQL实例
     * 缓存当前生效的GraphQL执行引擎，避免重复构建
     */
    private GraphQL currentGraphQL;
    
    /**
     * 当前Schema内容
     * 缓存当前生效的Schema定义内容，用于变更检测
     */
    private String currentSchemaContent;
    
    /**
     * 获取所有Schema定义
     * 
     * @return 系统中所有Schema定义的列表
     */
    public List<SchemaDefinition> getAllSchemas() {
        return schemaRepository.findAll();
    }
    
    /**
     * 获取当前激活状态的Schema定义
     * 
     * @return 当前激活状态的Schema定义列表
     */
    public List<SchemaDefinition> getActiveSchemas() {
        return schemaRepository.findByIsActiveTrue();
    }
    
    /**
     * 根据Schema名称查询Schema定义
     * 
     * @param schemaName Schema名称
     * @return Optional包装的Schema定义，如果不存在则为空
     */
    public Optional<SchemaDefinition> getSchemaByName(String schemaName) {
        return schemaRepository.findBySchemaName(schemaName);
    }
    
    /**
     * 创建新的Schema定义
     * 
     * 执行流程：
     * 1. 检查Schema名称唯一性
     * 2. 验证Schema语法正确性
     * 3. 创建Schema定义对象并持久化
     * 
     * @param schemaName Schema名称，必须唯一
     * @param schemaContent GraphQL Schema定义内容
     * @param description Schema描述信息
     * @return 创建成功的Schema定义对象
     * @throws RuntimeException 当Schema名称已存在或语法验证失败时抛出
     */
    public SchemaDefinition createSchema(String schemaName, String schemaContent, String description) {
        // 第一步：检查schema名称是否已存在
        if (schemaRepository.existsBySchemaName(schemaName)) {
            logger.warn("尝试创建重复的Schema名称: {}", schemaName);
            throw new RuntimeException("Schema名称已存在: " + schemaName);
        }
        
        // 第二步：验证schema语法
        validateSchemaContent(schemaContent);
        
        // 第三步：创建并保存Schema定义
        SchemaDefinition schema = new SchemaDefinition(schemaName, schemaContent, description);
        SchemaDefinition savedSchema = schemaRepository.save(schema);
        
        logger.info("成功创建Schema定义: {} (ID: {})", schemaName, savedSchema.getId());
        return savedSchema;
    }
    
    /**
     * 更新Schema定义
     * 
     * 执行流程：
     * 1. 验证Schema是否存在
     * 2. 验证新的Schema语法
     * 3. 更新内容并递增版本号
     * 4. 持久化更新结果
     * 
     * @param id Schema定义的唯一标识
     * @param schemaContent 新的GraphQL Schema内容
     * @param description 新的描述信息
     * @return 更新后的Schema定义对象
     * @throws RuntimeException 当Schema不存在或语法验证失败时抛出
     */
    public SchemaDefinition updateSchema(Long id, String schemaContent, String description) {
        // 第一步：查找并验证Schema存在性
        SchemaDefinition schema = schemaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Schema不存在: " + id));
        
        // 第二步：验证新的schema语法
        validateSchemaContent(schemaContent);
        
        // 第三步：更新Schema内容和版本信息
        String oldVersion = String.valueOf(schema.getVersion());
        schema.setSchemaContent(schemaContent);
        schema.setDescription(description);
        schema.setVersion(schema.getVersion() + 1);
        
        // 第四步：保存更新结果
        SchemaDefinition updatedSchema = schemaRepository.save(schema);
        
        logger.info("成功更新Schema定义: {} (版本: {} -> {})", 
                   schema.getSchemaName(), oldVersion, updatedSchema.getVersion());
        return updatedSchema;
    }
    
    /**
     * 激活指定的Schema定义
     * 
     * 执行流程：
     * 1. 验证目标Schema是否存在
     * 2. 停用所有当前激活的Schema
     * 3. 激活目标Schema
     * 4. 重新加载GraphQL配置
     * 
     * 注意：系统同时只能有一个Schema处于激活状态
     * 
     * @param id 要激活的Schema定义ID
     * @throws RuntimeException 当Schema不存在时抛出
     */
    public void activateSchema(Long id) {
        // 第一步：查找并验证目标Schema
        SchemaDefinition schema = schemaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Schema不存在: " + id));
        
        // 第二步：停用所有当前激活的schema
        List<SchemaDefinition> activeSchemas = schemaRepository.findByIsActiveTrue();
        for (SchemaDefinition activeSchema : activeSchemas) {
            activeSchema.setIsActive(false);
            schemaRepository.save(activeSchema);
            logger.debug("停用Schema: {}", activeSchema.getSchemaName());
        }
        
        // 第三步：激活目标schema
        schema.setIsActive(true);
        schemaRepository.save(schema);
        
        logger.info("成功激活Schema: {} (版本: {})", schema.getSchemaName(), schema.getVersion());
        
        // 第四步：重新加载GraphQL配置
        reloadGraphQLSchema();
    }
    
    /**
     * 停用指定的Schema定义
     * 
     * @param id 要停用的Schema定义ID
     * @throws RuntimeException 当Schema不存在时抛出
     */
    public void deactivateSchema(Long id) {
        // 查找并验证Schema存在性
        SchemaDefinition schema = schemaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Schema不存在: " + id));
        
        // 设置为非激活状态
        schema.setIsActive(false);
        schemaRepository.save(schema);
        
        logger.info("成功停用Schema: {}", schema.getSchemaName());
        
        // 重新加载GraphQL配置
        reloadGraphQLSchema();
    }
    
    /**
     * 删除Schema定义
     * 
     * 安全检查：
     * - 不允许删除当前激活状态的Schema
     * - 删除前需要先停用Schema
     * 
     * @param id 要删除的Schema定义ID
     * @throws RuntimeException 当Schema不存在或处于激活状态时抛出
     */
    public void deleteSchema(Long id) {
        // 查找并验证Schema存在性
        SchemaDefinition schema = schemaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Schema不存在: " + id));
        
        // 安全检查：不允许删除激活的Schema
        if (schema.getIsActive()) {
            logger.warn("尝试删除激活状态的Schema: {}", schema.getSchemaName());
            throw new RuntimeException("无法删除激活的Schema，请先停用");
        }
        
        // 执行删除操作
        String schemaName = schema.getSchemaName();
        schemaRepository.delete(schema);
        
        logger.info("成功删除Schema定义: {}", schemaName);
    }
    
    /**
     * 验证Schema内容的语法正确性
     * 
     * 验证步骤：
     * 1. 使用GraphQL Schema解析器进行语法检查
     * 2. 构建类型定义注册表验证类型完整性
     * 3. 可扩展自定义业务规则验证
     * 
     * @param schemaContent 待验证的GraphQL Schema内容
     * @throws RuntimeException 当Schema语法错误时抛出，包含详细错误信息
     */
    private void validateSchemaContent(String schemaContent) {
        try {
            // 使用GraphQL官方解析器进行语法验证
            SchemaParser schemaParser = new SchemaParser();
            TypeDefinitionRegistry typeRegistry = schemaParser.parse(schemaContent);
            
            // 基本的语法验证通过解析完成
            // TODO: 可以添加更多自定义验证逻辑
            // 例如：检查必需的根类型（Query、Mutation、Subscription）
            // 例如：验证字段类型的一致性
            // 例如：检查循环引用等复杂场景
            
            logger.debug("Schema语法验证通过，包含 {} 个类型定义", typeRegistry.types().size());
            
        } catch (Exception e) {
            logger.error("重新加载GraphQL Schema时发生错误", e);
            throw new RuntimeException("GraphQL Schema重新加载失败", e);
        }
    }
    
    /**
     * 基于resolver配置动态生成GraphQL Schema
     */
    private String generateDynamicSchema() {
        StringBuilder schema = new StringBuilder();
        
        // 添加基础类型定义
        schema.append("type Query {\n");
        
        // 获取所有查询类型的resolver配置
        List<com.example.dynamicdata.entity.ResolverConfig> queryResolvers = 
            resolverConfigService.getEnabledConfigsByOperationType("QUERY");
        
        if (queryResolvers.isEmpty()) {
            // 如果没有配置的query，添加一个默认的占位符
            schema.append("  _empty: String\n");
        } else {
            for (com.example.dynamicdata.entity.ResolverConfig config : queryResolvers) {
                schema.append("  ").append(config.getResolverName())
                      .append(": [User]\n"); // 简化处理，返回User类型数组
            }
        }
        
        schema.append("}\n\n");
        
        // 添加Mutation类型
        schema.append("type Mutation {\n");
        
        // 获取所有变更类型的resolver配置
        List<com.example.dynamicdata.entity.ResolverConfig> mutationResolvers = 
            resolverConfigService.getEnabledConfigsByOperationType("MUTATION");
        
        if (mutationResolvers.isEmpty()) {
            // 如果没有配置的mutation，添加一个默认的占位符
            schema.append("  _empty: String\n");
        } else {
            for (com.example.dynamicdata.entity.ResolverConfig config : mutationResolvers) {
                schema.append("  ").append(config.getResolverName())
                      .append("(input: String!): User\n"); // 简化处理
            }
        }
        
        schema.append("}\n\n");
        
        // 添加User类型定义
        schema.append("type User {\n");
        schema.append("  id: ID!\n");
        schema.append("  name: String\n");
        schema.append("  email: String\n");
        schema.append("}\n");
        
        logger.debug("动态生成的Schema: {}", schema.toString());
        return schema.toString();
    }
    
    private void handleSchemaReloadError(Exception e) {
        logger.error("Schema语法验证失败: {}", e.getMessage());
        throw new RuntimeException("Schema语法错误: " + e.getMessage(), e);
    }
    
    /**
     * 重新加载GraphQL Schema配置
     * 
     * 该方法是Schema管理的核心功能，负责将数据库中的Schema定义
     * 转换为可执行的GraphQL实例。执行流程：
     * 
     * 1. 获取所有激活状态的Schema定义
     * 2. 合并多个Schema内容为统一定义
     * 3. 检测Schema内容是否发生变化
     * 4. 解析Schema定义并构建类型注册表
     * 5. 获取解析器运行时配置
     * 6. 生成可执行的GraphQL Schema
     * 7. 创建新的GraphQL执行引擎实例
     * 
     * 性能优化：
     * - 内容变更检测：避免不必要的重新构建
     * - 缓存机制：保存当前生效的GraphQL实例
     * 
     * @throws RuntimeException 当Schema解析或构建失败时抛出
     */
    public void reloadGraphQLSchema() {
        try {
            // 第一步：基于resolver配置动态生成Schema
            String newSchemaContent = generateDynamicSchema();
            
            // 第二步：检查Schema内容是否有变化（性能优化）
            if (newSchemaContent.equals(currentSchemaContent)) {
                logger.debug("Schema内容未发生变化，跳过重新加载");
                return;
            }
            
            // 第三步：解析Schema定义并构建类型注册表
            SchemaParser schemaParser = new SchemaParser();
            TypeDefinitionRegistry typeRegistry = schemaParser.parse(newSchemaContent);
            
            // 第四步：获取解析器运行时配置
            RuntimeWiring runtimeWiring = buildRuntimeWiring();
            
            // 第五步：生成可执行的GraphQL Schema
            SchemaGenerator schemaGenerator = new SchemaGenerator();
            GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);
            
            // 第六步：创建新的GraphQL执行引擎实例
            currentGraphQL = GraphQL.newGraphQL(graphQLSchema).build();
            currentSchemaContent = newSchemaContent;
            
            logger.info("GraphQL Schema重新加载成功，动态生成Schema");
            
        } catch (Exception e) {
            logger.error("重新加载GraphQL Schema失败: {}", e.getMessage(), e);
            throw new RuntimeException("Schema重新加载失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 构建GraphQL运行时配置
     * 
     * 该方法负责构建GraphQL Schema的运行时配置，包括：
     * - 数据获取器（DataFetcher）的注册
     * - 类型解析器（TypeResolver）的配置
     * - 标量类型（Scalar）的定义
     * - 指令（Directive）的处理逻辑
     * 
     * 集成ResolverConfigService获取动态解析器配置
     * 
     * @return 构建完成的RuntimeWiring实例
     */
    private RuntimeWiring buildRuntimeWiring() {
        RuntimeWiring.Builder wiringBuilder = RuntimeWiring.newRuntimeWiring();
        
        // 从数据库获取所有启用的resolver配置
        List<com.example.dynamicdata.entity.ResolverConfig> configs = resolverConfigService.getEnabledConfigs();
        
        for (com.example.dynamicdata.entity.ResolverConfig config : configs) {
            if ("QUERY".equals(config.getOperationType())) {
                wiringBuilder.type("Query", typeBuilder ->
                    typeBuilder.dataFetcher(config.getResolverName(), createDataFetcher(config))
                );
            } else if ("MUTATION".equals(config.getOperationType())) {
                wiringBuilder.type("Mutation", typeBuilder ->
                    typeBuilder.dataFetcher(config.getResolverName(), createDataFetcher(config))
                );
            }
        }
        
        logger.info("Dynamic resolvers built successfully. Total configs: {}", configs.size());
        return wiringBuilder.build();
    }
    
    /**
     * 根据配置创建数据获取器
     * 
     * @param config resolver配置
     * @return DataFetcher实例
     */
    private DataFetcher<Object> createDataFetcher(com.example.dynamicdata.entity.ResolverConfig config) {
        return environment -> {
            try {
                Map<String, Object> arguments = environment.getArguments();
                logger.debug("Executing resolver: {} with arguments: {}", config.getResolverName(), arguments);
                
                if ("QUERY".equals(config.getOperationType())) {
                    return sqlExecutor.executeQuery(config.getSqlQuery(), arguments);
                } else if ("MUTATION".equals(config.getOperationType())) {
                    return sqlExecutor.executeUpdate(config.getSqlQuery(), arguments);
                } else {
                    throw new RuntimeException("Unsupported operation type: " + config.getOperationType());
                }
            } catch (Exception e) {
                logger.error("Error executing resolver: {}", config.getResolverName(), e);
                throw new RuntimeException("Failed to execute resolver: " + config.getResolverName(), e);
            }
        };
    }
    
    /**
     * 获取当前可用的GraphQL执行引擎实例
     * 
     * 该方法提供对当前生效GraphQL实例的访问，支持懒加载机制：
     * - 如果GraphQL实例尚未初始化，自动触发Schema加载
     * - 返回缓存的GraphQL实例以提高性能
     * 
     * 使用场景：
     * - GraphQL查询和变更操作的执行
     * - GraphQL端点的请求处理
     * - Schema验证和测试
     * 
     * @return 当前激活的GraphQL执行引擎实例，如果没有激活Schema则可能为null
     */
    public GraphQL getCurrentGraphQL() {
        // 懒加载机制：首次访问时自动加载Schema
        if (currentGraphQL == null) {
            logger.debug("GraphQL实例未初始化，触发Schema重新加载");
            reloadGraphQLSchema();
        }
        return currentGraphQL;
    }
    
    /**
     * 获取当前生效的Schema定义内容
     * 
     * 返回当前所有激活Schema合并后的完整定义字符串。
     * 该内容反映了当前GraphQL服务的完整类型系统定义。
     * 
     * 使用场景：
     * - Schema内容的调试和检查
     * - Schema变更检测和比较
     * - 开发工具的Schema展示
     * - 文档生成和API说明
     * 
     * @return 当前合并后的完整Schema定义字符串，如果没有激活Schema则返回null
     */
    public String getCurrentSchemaContent() {
        return currentSchemaContent;
    }
    
    /**
     * 合并多个Schema定义内容
     * 
     * 该工具方法用于将多个独立的GraphQL Schema定义合并为单一的Schema字符串。
     * 合并过程会在每个Schema定义之间添加换行符以确保语法正确性。
     * 
     * 合并规则：
     * - 按照输入顺序依次合并Schema内容
     * - 每个Schema定义后自动添加换行符分隔
     * - 保持原始Schema定义的格式和注释
     * - 自动过滤空内容和null值
     * 
     * 注意事项：
     * - 调用方需要确保Schema定义之间没有类型冲突
     * - 合并后的Schema需要通过语法验证
     * - 建议在合并前对各个Schema进行预验证
     * 
     * @param schemaContents 待合并的Schema定义内容列表
     * @return 合并后的完整Schema定义字符串，如果输入为空则返回空字符串
     */
    public String mergeSchemaContents(List<String> schemaContents) {
        // 参数验证
        if (schemaContents == null || schemaContents.isEmpty()) {
            logger.debug("Schema内容列表为空，返回空字符串");
            return "";
        }
        
        StringBuilder merged = new StringBuilder();
        int validSchemaCount = 0;
        
        // 逐个合并Schema内容
        for (String content : schemaContents) {
            // 过滤空内容和null值
            if (content != null && !content.trim().isEmpty()) {
                merged.append(content);
                // 确保每个Schema定义后有换行符分隔
                if (!content.endsWith("\n")) {
                    merged.append("\n");
                }
                validSchemaCount++;
            }
        }
        
        String result = merged.toString();
        logger.debug("成功合并 {} 个有效Schema定义，总长度: {} 字符", validSchemaCount, result.length());
        
        return result;
    }
}