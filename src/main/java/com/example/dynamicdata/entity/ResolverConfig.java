package com.example.dynamicdata.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * GraphQL解析器配置实体类
 * 
 * 该实体类用于存储和管理GraphQL解析器的配置信息，是动态数据服务的核心组件之一。
 * 通过配置化的方式定义GraphQL查询和变更操作，实现了GraphQL Schema与数据库操作的
 * 动态映射，支持灵活的数据访问和业务逻辑处理。
 * 
 * 核心功能：
 * 1. 解析器定义 - 配置GraphQL字段对应的数据库操作
 * 2. SQL映射 - 将GraphQL查询转换为SQL执行
 * 3. 参数管理 - 定义输入参数和输出字段的结构
 * 4. 操作类型控制 - 支持查询(QUERY)和变更(MUTATION)操作
 * 5. 动态启用/禁用 - 运行时控制解析器的可用性
 * 6. 参数验证 - 通过Bean Validation确保数据完整性
 * 
 * 数据库映射：
 * - 表名：resolver_config
 * - 主键：id（自增长）
 * - 唯一约束：resolver_name
 * - 文本字段：sql_query、input_parameters、output_fields（TEXT类型）
 * 
 * 业务场景：
 * - 动态GraphQL API的配置和管理
 * - 数据库查询的GraphQL化封装
 * - 多租户环境下的个性化数据访问
 * - API网关的数据聚合和转换
 * - 微服务架构中的数据编排
 * - 低代码/无代码平台的数据接口生成
 * 
 * 安全特性：
 * - SQL注入防护（参数化查询）
 * - 输入验证和格式检查
 * - 操作类型限制和权限控制
 * - 解析器启用状态管理
 * 
 * 配置示例：
 * ```java
 * ResolverConfig config = new ResolverConfig();
 * config.setResolverName("getUserById");
 * config.setOperationType("QUERY");
 * config.setSqlQuery("SELECT * FROM users WHERE id = #{userId}");
 * config.setInputParameters("{\"userId\": \"Long\"}");
 * config.setOutputFields("{\"id\": \"Long\", \"name\": \"String\"}");
 * config.setEnabled(true);
 * ```
 * 
 * 注意事项：
 * - 解析器名称必须与GraphQL Schema中的字段名一致
 * - SQL查询必须使用参数化形式，避免SQL注入
 * - 输入参数和输出字段定义必须是有效的JSON格式
 * - 操作类型必须明确指定为QUERY或MUTATION
 * - 启用状态变更会影响GraphQL查询的可用性
 * 
 * @author Dynamic Data Service Team
 * @version 1.0
 * @since 2024-01-01
 */
@Entity
@Table(name = "resolver_config")
public class ResolverConfig {
    
    /**
     * 解析器配置的唯一标识符
     * 
     * 使用数据库自增策略生成，作为解析器配置记录的主键。
     * 该ID在整个系统中唯一，用于关联和引用特定的解析器配置。
     * 
     * JPA注解说明：
     * - @Id: 标识为主键字段
     * - @GeneratedValue: 指定主键生成策略
     * - GenerationType.IDENTITY: 使用数据库的自增功能
     * 
     * 使用场景：
     * - 解析器配置的唯一标识和引用
     * - 数据库关联查询的外键
     * - 缓存系统的键值标识
     * - 日志记录和错误追踪
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * GraphQL解析器的名称标识
     * 
     * 该名称必须与GraphQL Schema中定义的字段名完全一致，用于建立GraphQL查询
     * 与数据库操作之间的映射关系。系统通过此名称查找对应的解析器配置。
     * 
     * 命名规范：
     * - 必须以字母开头（a-z, A-Z）
     * - 只能包含字母、数字和下划线
     * - 长度限制在1-100个字符之间
     * - 采用驼峰命名法，如：getUserById、updateUserInfo
     * - 避免使用GraphQL保留字和系统关键字
     * 
     * 验证规则：
     * - @NotBlank: 不能为空或仅包含空白字符
     * - @Size: 长度必须在指定范围内
     * - @Pattern: 必须符合正则表达式规则
     * - @Column(unique=true): 数据库层面的唯一性约束
     * 
     * 使用示例：
     * - "users" - 查询用户列表
     * - "userById" - 根据ID查询单个用户
     * - "createUser" - 创建新用户
     * - "updateUserProfile" - 更新用户资料
     * 
     * 注意事项：
     * - 名称一旦设定，修改时需要同步更新GraphQL Schema
     * - 建议使用有意义的业务名称，便于理解和维护
     * - 避免过长的名称，影响GraphQL查询的可读性
     */
    @NotBlank(message = "解析器名称不能为空")
    @Size(min = 1, max = 100, message = "解析器名称长度必须在1-100个字符之间")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]*$", message = "解析器名称必须以字母开头，只能包含字母、数字和下划线")
    @Column(nullable = false, unique = true)
    private String resolverName;
    
    /**
     * GraphQL操作类型标识
     * 
     * 定义该解析器对应的GraphQL操作类型，决定了解析器在GraphQL Schema中的位置
     * 和执行方式。不同的操作类型有不同的语义和使用场景。
     * 
     * 支持的操作类型：
     * - QUERY: 查询操作，用于数据读取，对应GraphQL的Query根类型
     *   * 特点：只读操作，不修改数据状态
     *   * 场景：数据查询、列表获取、详情查看等
     *   * 示例：获取用户信息、查询订单列表、统计数据等
     * 
     * - MUTATION: 变更操作，用于数据修改，对应GraphQL的Mutation根类型
     *   * 特点：可修改数据状态，包括增删改操作
     *   * 场景：数据创建、更新、删除等
     *   * 示例：创建用户、更新订单状态、删除商品等
     * 
     * 操作类型的影响：
     * - 决定解析器在GraphQL Schema中的归属
     * - 影响GraphQL查询的语法结构
     * - 决定缓存策略和执行优化方式
     * - 影响权限控制和安全策略
     * 
     * 验证规则：
     * - @NotBlank: 必须指定操作类型
     * - @Pattern: 只允许QUERY或MUTATION两种值
     * - @Column(nullable=false): 数据库层面不允许为空
     * 
     * 最佳实践：
     * - 查询操作统一使用QUERY类型
     * - 所有数据修改操作使用MUTATION类型
     * - 避免在QUERY中执行修改操作
     * - 根据业务语义选择合适的操作类型
     */
    @NotBlank(message = "操作类型不能为空")
    @Pattern(regexp = "^(QUERY|MUTATION)$", message = "操作类型只能是QUERY或MUTATION")
    @Column(nullable = false)
    private String operationType;
    
    /**
     * 参数化SQL查询语句
     * 
     * 存储与GraphQL解析器对应的SQL查询语句，支持参数化查询以防止SQL注入攻击。
     * 该字段是解析器配置的核心，定义了GraphQL查询如何转换为数据库操作。
     * 
     * 参数化语法：
     * - 使用 #{paramName} 作为参数占位符
     * - 参数名必须与inputParameters中定义的参数名一致
     * - 支持多个参数，如：#{userId}, #{status}, #{createTime}
     * 
     * SQL语句类型：
     * - SELECT查询：用于QUERY操作类型
     *   * 示例："SELECT id, name, email FROM users WHERE id = #{userId}"
     * - INSERT语句：用于MUTATION操作类型
     *   * 示例："INSERT INTO users (name, email) VALUES (#{name}, #{email})"
     * - UPDATE语句：用于MUTATION操作类型
     *   * 示例："UPDATE users SET name = #{name} WHERE id = #{userId}"
     * - DELETE语句：用于MUTATION操作类型
     *   * 示例："DELETE FROM users WHERE id = #{userId}"
     * 
     * 安全特性：
     * - 强制使用参数化查询，防止SQL注入
     * - 参数类型验证和格式检查
     * - SQL语句长度限制，防止过大查询
     * - 禁止动态SQL拼接，确保查询安全
     * 
     * 性能优化：
     * - 支持数据库查询计划缓存
     * - 建议使用索引优化查询性能
     * - 避免复杂的子查询和联表操作
     * - 合理使用LIMIT限制结果集大小
     * 
     * 验证规则：
     * - @NotBlank: SQL语句不能为空
     * - @Size: 长度限制在10-10000字符之间
     * - @Column(TEXT): 使用TEXT类型支持长SQL语句
     * 
     * 使用示例：
     * ```sql
     * -- 查询用户信息
     * SELECT id, name, email, created_at FROM users 
     * WHERE id = #{userId} AND status = #{status}
     * 
     * -- 创建新用户
     * INSERT INTO users (name, email, phone, created_at) 
     * VALUES (#{name}, #{email}, #{phone}, NOW())
     * 
     * -- 更新用户状态
     * UPDATE users SET status = #{status}, updated_at = NOW() 
     * WHERE id = #{userId}
     * ```
     * 
     * 注意事项：
     * - 必须使用参数化查询，不允许字符串拼接
     * - 参数名称要与输入参数定义保持一致
     * - 查询结果字段要与输出字段定义匹配
     * - 避免使用数据库特定的语法，保持兼容性
     */
    @NotBlank(message = "SQL查询语句不能为空")
    @Size(min = 10, max = 10000, message = "SQL查询语句长度必须在10-10000个字符之间")
    @Column(columnDefinition = "TEXT", nullable = false)
    private String sqlQuery;
    
    /**
     * 解析器描述信息
     * 用于说明该解析器的用途和功能，可选字段
     */
    @Size(max = 1000, message = "描述信息长度不能超过1000个字符")
    @Column(columnDefinition = "TEXT")
    private String description;
    
    /**
     * 输入参数定义
     * JSON格式字符串，定义GraphQL查询可接受的参数及其类型
     * 例如：{"userId": "Long", "name": "String"}
     */
    @Size(max = 5000, message = "输入参数定义长度不能超过5000个字符")
    @Column(name = "input_parameters", columnDefinition = "TEXT")
    private String inputParameters;
    
    /**
     * 输出字段定义
     * JSON格式字符串，定义查询结果的字段结构和数据类型
     */
    @Size(max = 5000, message = "输出字段定义长度不能超过5000个字符")
    @Column(name = "output_fields", columnDefinition = "TEXT")
    private String outputFields;
    
    /**
     * 是否启用该解析器
     * true：启用，false：禁用，默认值为true
     */
    @NotNull(message = "启用状态不能为空")
    @Column(nullable = false)
    private Boolean enabled = true;
    
    /**
     * 创建时间
     * 记录解析器配置的创建时间，由JPA生命周期回调自动设置
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     * 记录解析器配置的最后更新时间，由JPA生命周期回调自动设置
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    /**
     * JPA生命周期回调方法
     * 在实体持久化之前执行，设置创建时间和更新时间
     */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    /**
     * JPA生命周期回调方法
     * 在实体更新之前执行，更新最后修改时间
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ==================== 构造方法 ====================
    
    /**
     * 默认无参构造方法
     * JPA规范要求，用于实体的反射创建和ORM框架的对象实例化
     */
    public ResolverConfig() {
    }

    /**
     * 带核心参数的构造方法
     * 
     * @param resolverName 解析器名称，使用驼峰命名法
     * @param operationType 操作类型，"QUERY"或"MUTATION"
     * @param sqlQuery SQL查询语句，使用#{paramName}作为参数占位符
     */
    public ResolverConfig(String resolverName, String operationType, String sqlQuery) {
        this.resolverName = resolverName;
        this.operationType = operationType;
        this.sqlQuery = sqlQuery;
        this.enabled = true;
    }

    // ==================== Getter和Setter方法 ====================
    
    /**
     * 获取主键ID
     * @return 主键ID
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置主键ID
     * @param id 主键ID
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取解析器名称
     * @return 解析器名称
     */
    public String getResolverName() {
        return resolverName;
    }

    /**
     * 设置解析器名称
     * @param resolverName 解析器名称
     */
    public void setResolverName(String resolverName) {
        this.resolverName = resolverName;
    }

    /**
     * 获取操作类型
     * @return 操作类型
     */
    public String getOperationType() {
        return operationType;
    }

    /**
     * 设置操作类型
     * @param operationType 操作类型
     */
    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    /**
     * 获取SQL查询语句
     * @return SQL查询语句
     */
    public String getSqlQuery() {
        return sqlQuery;
    }

    /**
     * 设置SQL查询语句
     * @param sqlQuery SQL查询语句
     */
    public void setSqlQuery(String sqlQuery) {
        this.sqlQuery = sqlQuery;
    }

    /**
     * 获取描述信息
     * @return 描述信息
     */
    public String getDescription() {
        return description;
    }

    /**
     * 设置描述信息
     * @param description 描述信息
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * 获取输入参数定义
     * @return 输入参数定义
     */
    public String getInputParameters() {
        return inputParameters;
    }

    /**
     * 设置输入参数定义
     * @param inputParameters 输入参数定义
     */
    public void setInputParameters(String inputParameters) {
        this.inputParameters = inputParameters;
    }

    /**
     * 获取输出字段定义
     * @return 输出字段定义
     */
    public String getOutputFields() {
        return outputFields;
    }

    /**
     * 设置输出字段定义
     * @param outputFields 输出字段定义
     */
    public void setOutputFields(String outputFields) {
        this.outputFields = outputFields;
    }

    /**
     * 获取启用状态
     * @return 启用状态
     */
    public Boolean getEnabled() {
        return enabled;
    }

    /**
     * 设置启用状态
     * @param enabled 启用状态
     */
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取创建时间
     * @return 创建时间
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * 设置创建时间
     * @param createdAt 创建时间
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * 获取更新时间
     * @return 更新时间
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * 设置更新时间
     * @param updatedAt 更新时间
     */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}