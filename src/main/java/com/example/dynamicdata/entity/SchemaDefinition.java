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

/**
 * GraphQL Schema定义实体类
 * 
 * 该实体类用于存储和管理GraphQL Schema的定义信息，支持Schema的版本控制、
 * 状态管理和动态更新。每个Schema定义包含完整的GraphQL类型定义、查询、
 * 变更和订阅操作的描述。
 * 
 * 核心功能：
 * 1. Schema内容存储 - 保存完整的GraphQL Schema定义文本
 * 2. 版本控制 - 支持Schema的版本管理和历史追踪
 * 3. 状态管理 - 控制Schema的激活/停用状态
 * 4. 元数据管理 - 记录Schema的创建时间、更新时间等信息
 * 5. 唯一性约束 - 确保Schema名称的唯一性
 * 
 * 数据库映射：
 * - 表名：schema_definitions
 * - 主键：id（自增长）
 * - 唯一约束：schema_name
 * - 文本字段：schema_content（TEXT类型，支持大容量Schema定义）
 * 
 * 业务场景：
 * - 动态GraphQL Schema的定义和管理
 * - Schema版本的升级和回滚
 * - 多环境Schema配置的同步
 * - Schema变更的审计和追踪
 * - 开发、测试、生产环境的Schema隔离
 * 
 * 生命周期管理：
 * - 创建时自动设置创建时间和更新时间
 * - 更新时自动更新修改时间
 * - 支持软删除（通过isActive字段控制）
 * - 版本号自动递增管理
 * 
 * 使用示例：
 * ```java
 * SchemaDefinition schema = new SchemaDefinition(
 *     "user-service-schema",
 *     "type Query { users: [User] }",
 *     "用户服务的GraphQL Schema定义"
 * );
 * schema.setVersion(2);
 * schema.setIsActive(true);
 * ```
 * 
 * 注意事项：
 * - Schema内容应符合GraphQL规范
 * - Schema名称必须全局唯一
 * - 版本号应按顺序递增
 * - 激活状态的Schema才会被系统使用
 * 
 * @author Dynamic Data Service Team
 * @version 1.0
 * @since 2024-01-15
 */
@Entity
@Table(name = "schema_definitions")
public class SchemaDefinition {
    
    /**
     * Schema定义的唯一标识符
     * 
     * 使用数据库自增策略生成，作为Schema定义记录的主键。
     * 该ID在整个系统中唯一，用于关联和引用特定的Schema定义。
     * 
     * JPA注解说明：
     * - @Id: 标识为主键字段
     * - @GeneratedValue: 指定主键生成策略
     * - GenerationType.IDENTITY: 使用数据库的自增功能
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Schema定义的名称
     * 
     * Schema的业务标识名称，必须在系统中保持唯一性。
     * 通常采用有意义的命名规则，如："user-service"、"order-api"等。
     * 
     * 命名规范：
     * - 建议使用小写字母和连字符
     * - 避免使用特殊字符和空格
     * - 名称应具有业务含义，便于识别
     * - 长度建议控制在50个字符以内
     * 
     * 数据库约束：
     * - NOT NULL: 不允许为空
     * - UNIQUE: 全局唯一约束
     * 
     * 使用场景：
     * - Schema的查找和引用
     * - 配置文件中的Schema标识
     * - 日志记录和错误追踪
     * - API文档和界面显示
     */
    @Column(name = "schema_name", nullable = false, unique = true)
    private String schemaName;
    
    /**
     * GraphQL Schema的完整定义内容
     * 
     * 存储完整的GraphQL Schema定义文本，包括类型定义、查询、变更、订阅等。
     * 该字段是Schema定义的核心内容，必须符合GraphQL规范语法。
     * 
     * 内容格式：
     * - 标准的GraphQL Schema Definition Language (SDL)
     * - 包含type、interface、union、enum、input等类型定义
     * - 包含Query、Mutation、Subscription根类型
     * - 支持指令(directive)和注释
     * 
     * 存储特性：
     * - 使用TEXT类型支持大容量内容（通常可达65KB）
     * - 保持原始格式和缩进，便于阅读和维护
     * - 支持UTF-8编码，可包含中文注释
     * 
     * 示例内容：
     * ```graphql
     * type Query {
     *   users: [User!]!
     *   user(id: ID!): User
     * }
     * 
     * type User {
     *   id: ID!
     *   name: String!
     *   email: String
     * }
     * ```
     * 
     * 验证要求：
     * - 必须是有效的GraphQL SDL语法
     * - 不能为空或null
     * - 建议包含基本的Query类型定义
     */
    @Column(name = "schema_content", columnDefinition = "TEXT", nullable = false)
    private String schemaContent;
    
    /**
     * Schema定义的描述信息
     * 
     * 提供Schema的详细描述，包括用途、功能、适用场景等信息。
     * 该字段为可选字段，用于增强Schema的可读性和可维护性。
     * 
     * 描述内容建议：
     * - Schema的业务用途和功能概述
     * - 主要的GraphQL类型和操作说明
     * - 适用的业务场景和使用限制
     * - 版本变更的重要说明
     * - 依赖关系和集成注意事项
     * 
     * 使用场景：
     * - 开发文档和API说明
     * - Schema管理界面的展示
     * - 团队协作和知识传递
     * - 变更记录和版本说明
     * 
     * 示例：
     * "用户管理服务的GraphQL Schema，提供用户查询、创建、更新等操作，
     *  支持用户基本信息、权限管理和关联数据的查询。"
     */
    @Column(name = "description")
    private String description;
    
    /**
     * Schema的激活状态标识
     * 
     * 控制Schema是否处于激活状态，只有激活状态的Schema才会被系统使用。
     * 默认值为true，表示新创建的Schema默认为激活状态。
     * 
     * 状态说明：
     * - true: 激活状态，Schema可以被GraphQL引擎使用
     * - false: 停用状态，Schema不会被加载和执行
     * 
     * 使用场景：
     * - Schema的上线和下线控制
     * - 版本切换和灰度发布
     * - 紧急情况下的快速禁用
     * - 开发和测试环境的Schema隔离
     * 
     * 业务逻辑：
     * - 系统启动时只加载激活状态的Schema
     * - Schema更新时可先设为停用状态进行验证
     * - 支持多个版本的Schema并存，通过激活状态控制使用
     * 
     * 注意事项：
     * - 停用Schema前应确保没有正在使用的GraphQL查询
     * - 建议在低峰期进行Schema状态切换
     * - 状态变更应记录操作日志
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    /**
     * Schema的版本号
     * 
     * 用于标识Schema定义的版本，支持Schema的版本控制和历史管理。
     * 默认值为1，表示初始版本，后续版本应递增。
     * 
     * 版本管理策略：
     * - 版本号从1开始，每次更新递增
     * - 主要变更（破坏性更改）建议大幅递增版本号
     * - 次要变更（兼容性更改）可小幅递增版本号
     * - 支持版本回滚和历史查询
     * 
     * 使用场景：
     * - Schema变更的历史追踪
     * - 版本比较和差异分析
     * - 回滚到指定版本
     * - API兼容性管理
     * - 变更审计和合规要求
     * 
     * 版本控制最佳实践：
     * - 重大变更前备份当前版本
     * - 记录版本变更的详细说明
     * - 测试新版本的兼容性
     * - 渐进式发布和灰度验证
     * 
     * 示例版本规划：
     * - v1: 初始版本，基础功能
     * - v2: 添加新的查询类型
     * - v3: 修改字段定义，破坏性变更
     */
    @Column(name = "version", nullable = false)
    private Integer version = 1;
    
    /**
     * Schema定义的创建时间
     * 
     * 记录Schema定义首次创建的精确时间戳，用于审计和历史追踪。
     * 该字段在实体首次持久化时自动设置，不允许为空。
     * 
     * 时间特性：
     * - 使用LocalDateTime类型，精确到纳秒级别
     * - 基于服务器本地时区的时间
     * - 创建后不可修改，保证数据的完整性
     * 
     * 使用场景：
     * - 数据审计和合规要求
     * - Schema创建历史的统计分析
     * - 按时间范围查询Schema
     * - 数据备份和恢复的时间参考
     * - 性能分析和容量规划
     * 
     * 自动设置：
     * - 通过@PrePersist回调方法自动设置
     * - 确保每个Schema都有准确的创建时间
     * - 避免手动设置可能导致的时间不一致
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    /**
     * Schema定义的最后更新时间
     * 
     * 记录Schema定义最近一次修改的时间戳，用于跟踪变更历史。
     * 该字段在实体创建和每次更新时自动设置。
     * 
     * 更新触发条件：
     * - Schema内容的任何修改
     * - 描述信息的更新
     * - 激活状态的变更
     * - 版本号的调整
     * 
     * 使用场景：
     * - 变更历史的追踪和分析
     * - 缓存失效的时间判断
     * - 数据同步的增量更新
     * - 最近活跃Schema的识别
     * - 变更频率的统计分析
     * 
     * 自动维护：
     * - 创建时通过@PrePersist设置初始值
     * - 更新时通过@PreUpdate自动更新
     * - 确保时间戳的准确性和一致性
     * 
     * 注意事项：
     * - 该字段可以为null（历史数据兼容）
     * - 建议在查询时使用该字段进行排序
     * - 可用于实现乐观锁机制
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    /**
     * 实体创建前的回调方法
     * 
     * 该方法在实体首次持久化到数据库之前自动执行，用于设置创建时间和初始更新时间。
     * JPA会在调用EntityManager.persist()方法时自动触发此回调。
     * 
     * 执行时机：
     * - 在INSERT SQL语句执行之前
     * - 在事务提交之前
     * - 每个实体实例只执行一次
     * 
     * 设置内容：
     * - createdAt: 设置为当前系统时间，记录创建时刻
     * - updatedAt: 初始设置为创建时间，保持一致性
     * 
     * 注解说明：
     * - @PrePersist: JPA生命周期回调注解
     * - protected: 访问修饰符，允许子类访问但限制外部调用
     * 
     * 最佳实践：
     * - 使用LocalDateTime.now()获取当前时间
     * - 避免在此方法中执行复杂的业务逻辑
     * - 确保方法执行的幂等性
     */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    /**
     * 实体更新前的回调方法
     * 
     * 该方法在实体更新到数据库之前自动执行，用于更新最后修改时间。
     * JPA会在调用EntityManager.merge()或实体状态变更时自动触发此回调。
     * 
     * 执行时机：
     * - 在UPDATE SQL语句执行之前
     * - 在事务提交之前
     * - 每次实体更新时都会执行
     * 
     * 更新内容：
     * - updatedAt: 更新为当前系统时间
     * - createdAt: 保持不变，维护原始创建时间
     * 
     * 触发条件：
     * - 实体字段值发生变化
     * - 显式调用merge()方法
     * - 在托管状态下修改实体属性
     * 
     * 注解说明：
     * - @PreUpdate: JPA生命周期回调注解
     * - protected: 限制访问范围，防止外部误调用
     * 
     * 使用效果：
     * - 自动维护数据的时间戳
     * - 支持变更历史的追踪
     * - 提供缓存失效的时间依据
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // ==================== 构造方法 ====================
    
    /**
     * 默认无参构造方法
     * 
     * JPA规范要求实体类必须提供无参构造方法，用于框架的反射实例化。
     * 该构造方法创建一个空的SchemaDefinition实例，所有字段使用默认值。
     * 
     * 默认值说明：
     * - id: null（由数据库自动生成）
     * - schemaName: null（需要后续设置）
     * - schemaContent: null（需要后续设置）
     * - description: null（可选字段）
     * - isActive: true（默认激活状态）
     * - version: 1（初始版本）
     * - createdAt: null（@PrePersist时自动设置）
     * - updatedAt: null（@PrePersist时自动设置）
     * 
     * 使用场景：
     * - JPA框架的实体实例化
     * - 反序列化操作
     * - 单元测试中的对象创建
     * - 需要逐步设置属性的场景
     */
    public SchemaDefinition() {}
    
    /**
     * 带参数的构造方法
     * 
     * 创建SchemaDefinition实例并设置核心属性，适用于快速创建完整的Schema定义。
     * 其他属性（如isActive、version等）使用默认值，时间戳字段由JPA回调自动设置。
     * 
     * 参数验证建议：
     * - schemaName不应为null或空字符串
     * - schemaContent不应为null或空字符串
     * - description可以为null（可选描述）
     * 
     * 使用示例：
     * ```java
     * SchemaDefinition schema = new SchemaDefinition(
     *     "user-api",
     *     "type Query { users: [User] }",
     *     "用户API的GraphQL Schema定义"
     * );
     * ```
     * 
     * @param schemaName Schema的唯一名称，不能为空
     * @param schemaContent 完整的GraphQL Schema定义内容，不能为空
     * @param description Schema的描述信息，可以为null
     */
    public SchemaDefinition(String schemaName, String schemaContent, String description) {
        this.schemaName = schemaName;
        this.schemaContent = schemaContent;
        this.description = description;
    }
    
    // ==================== Getter和Setter方法 ====================
    
    /**
     * 获取Schema定义的唯一标识符
     * 
     * @return Schema定义的主键ID，如果是新创建的实体则返回null
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置Schema定义的唯一标识符
     * 
     * 注意：通常不需要手动设置此值，由数据库自动生成
     * 
     * @param id Schema定义的主键ID
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取Schema定义的名称
     * 
     * @return Schema的业务标识名称
     */
    public String getSchemaName() {
        return schemaName;
    }

    /**
     * 设置Schema定义的名称
     * 
     * 设置时应确保名称的唯一性和符合命名规范
     * 
     * @param schemaName Schema的业务标识名称，不能为null或空字符串
     */
    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    /**
     * 获取GraphQL Schema的完整定义内容
     * 
     * @return 完整的GraphQL Schema定义文本
     */
    public String getSchemaContent() {
        return schemaContent;
    }

    /**
     * 设置GraphQL Schema的完整定义内容
     * 
     * 设置时应确保内容符合GraphQL SDL语法规范
     * 
     * @param schemaContent 完整的GraphQL Schema定义文本，不能为null或空字符串
     */
    public void setSchemaContent(String schemaContent) {
        this.schemaContent = schemaContent;
    }

    /**
     * 获取Schema定义的描述信息
     * 
     * @return Schema的描述信息，可能为null
     */
    public String getDescription() {
        return description;
    }

    /**
     * 设置Schema定义的描述信息
     * 
     * @param description Schema的描述信息，可以为null
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * 获取Schema的激活状态
     * 
     * @return true表示激活状态，false表示停用状态
     */
    public Boolean getIsActive() {
        return isActive;
    }

    /**
     * 设置Schema的激活状态
     * 
     * 状态变更时应考虑对正在运行的GraphQL查询的影响
     * 
     * @param isActive true表示激活状态，false表示停用状态
     */
    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    /**
     * 获取Schema的版本号
     * 
     * @return Schema的版本号，默认为1
     */
    public Integer getVersion() {
        return version;
    }

    /**
     * 设置Schema的版本号
     * 
     * 版本号应按递增顺序设置，用于版本控制和历史管理
     * 
     * @param version Schema的版本号，应大于0
     */
    public void setVersion(Integer version) {
        this.version = version;
    }

    /**
     * 获取Schema定义的创建时间
     * 
     * @return Schema定义的创建时间戳
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * 设置Schema定义的创建时间
     * 
     * 注意：通常不需要手动设置此值，由@PrePersist回调自动设置
     * 
     * @param createdAt Schema定义的创建时间戳
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * 获取Schema定义的最后更新时间
     * 
     * @return Schema定义的最后更新时间戳，可能为null
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * 设置Schema定义的最后更新时间
     * 
     * 注意：通常不需要手动设置此值，由@PreUpdate回调自动更新
     * 
     * @param updatedAt Schema定义的最后更新时间戳
     */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}