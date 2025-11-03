package com.example.dynamicdata.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.example.dynamicdata.entity.ResolverConfig;

/**
 * GraphQL解析器配置数据访问层接口
 * 
 * 该接口继承自Spring Data JPA的JpaRepository，提供解析器配置实体的完整数据访问功能。
 * 解析器配置用于定义GraphQL字段的数据获取逻辑，包括SQL查询、参数映射和结果处理等。
 * 
 * 核心功能：
 * 1. 解析器配置的基础CRUD操作（继承自JpaRepository）
 * 2. 基于解析器名称的精确查询
 * 3. 启用状态解析器的批量查询
 * 4. 按操作类型分类的解析器查询
 * 5. 组合条件的自定义JPQL查询
 * 
 * 业务场景：
 * - GraphQL字段解析器的动态配置管理
 * - 解析器的启用/禁用状态控制
 * - 按操作类型（Query、Mutation、Subscription）分类管理
 * - 解析器配置的批量加载和缓存
 * - 运行时解析器配置的动态更新
 * 
 * 解析器配置特性：
 * - 支持SQL查询模板的动态配置
 * - 参数映射和类型转换的灵活定义
 * - 结果集处理和数据转换的自定义逻辑
 * - 缓存策略和性能优化配置
 * - 权限控制和安全验证集成
 * 
 * 技术实现：
 * - 基于Spring Data JPA的声明式数据访问
 * - 支持方法名称约定的自动查询生成
 * - 支持@Query注解的自定义JPQL查询
 * - 自动事务管理和连接池优化
 * - 类型安全的查询结果映射
 * 
 * @author Dynamic Data Service Team
 * @version 1.0
 * @since 2024-01-15
 */
@Repository
public interface ResolverConfigRepository extends JpaRepository<ResolverConfig, Long> {
    
    /**
     * 根据解析器名称查找解析器配置
     * 
     * 该方法用于通过解析器名称精确查找对应的解析器配置记录。
     * 解析器名称通常对应GraphQL Schema中的字段名称，具有业务唯一性。
     * 
     * 使用场景：
     * - GraphQL字段解析时获取对应的解析器配置
     * - 解析器配置的存在性验证
     * - 动态解析器加载和缓存
     * - 解析器配置的更新和维护
     * 
     * 命名约定：
     * - 解析器名称通常采用驼峰命名法
     * - 与GraphQL Schema中的字段名称保持一致
     * - 支持嵌套字段的点分隔命名（如：user.profile.avatar）
     * 
     * @param resolverName 解析器名称，不能为null或空字符串
     * @return Optional包装的解析器配置，如果不存在则为空Optional
     */
    Optional<ResolverConfig> findByResolverName(String resolverName);
    
    /**
     * 查找所有启用状态的解析器配置
     * 
     * 该方法返回系统中所有enabled字段为true的解析器配置记录。
     * 只有启用状态的解析器配置才会在GraphQL查询执行时被使用。
     * 
     * 使用场景：
     * - 系统启动时加载所有有效的解析器配置
     * - GraphQL Schema构建时获取可用解析器
     * - 解析器配置的批量验证和检查
     * - 性能监控和统计分析
     * 
     * 性能考虑：
     * - 该查询结果通常会被缓存以提高性能
     * - 建议在解析器配置变更时清理相关缓存
     * - 可配合分页查询处理大量解析器配置
     * 
     * 数据一致性：
     * - 启用状态的解析器必须具有完整的配置信息
     * - SQL查询模板和参数映射不能为空
     * - 必须通过配置验证才能设置为启用状态
     * 
     * @return 所有启用状态的解析器配置列表，如果没有启用的解析器则返回空列表
     */
    List<ResolverConfig> findByEnabledTrue();
    
    /**
     * 根据操作类型查找解析器配置
     * 
     * 该方法按照GraphQL操作类型分类查找解析器配置。
     * GraphQL支持三种基本操作类型：Query（查询）、Mutation（变更）、Subscription（订阅）。
     * 
     * 操作类型说明：
     * - Query: 数据查询操作，只读访问，支持复杂的关联查询
     * - Mutation: 数据变更操作，包括创建、更新、删除等写操作
     * - Subscription: 实时订阅操作，支持数据变更的实时推送
     * 
     * 使用场景：
     * - 按操作类型构建不同的GraphQL Schema部分
     * - 权限控制和安全验证的分类管理
     * - 性能优化和缓存策略的差异化配置
     * - 监控和日志记录的分类统计
     * 
     * 配置特点：
     * - Query类型解析器通常配置为只读SQL查询
     * - Mutation类型解析器包含数据修改的SQL语句
     * - Subscription类型解析器需要配置事件监听机制
     * 
     * @param operationType GraphQL操作类型（Query/Mutation/Subscription）
     * @return 指定操作类型的解析器配置列表，如果没有匹配的配置则返回空列表
     */
    List<ResolverConfig> findByOperationType(String operationType);
    
    /**
     * 查找指定操作类型的启用状态解析器配置
     * 
     * 该方法使用自定义JPQL查询，组合了启用状态和操作类型两个过滤条件，
     * 返回既处于启用状态又属于指定操作类型的解析器配置列表。
     * 
     * JPQL查询说明：
     * - SELECT r FROM ResolverConfig r: 查询ResolverConfig实体
     * - WHERE r.enabled = true: 过滤启用状态的记录
     * - AND r.operationType = ?1: 同时过滤指定的操作类型，?1为第一个参数
     * 
     * 查询优化：
     * - 组合索引：建议在(enabled, operationType)字段上创建复合索引
     * - 查询缓存：该查询结果适合进行二级缓存
     * - 参数绑定：使用位置参数(?1)提高查询性能
     * 
     * 使用场景：
     * - GraphQL Schema构建时按类型加载有效解析器
     * - 运行时解析器的分类获取和执行
     * - 解析器配置的动态热加载
     * - 系统性能监控和解析器使用统计
     * 
     * 业务逻辑：
     * - 只有同时满足启用状态和操作类型的解析器才会被返回
     * - 确保返回的解析器配置都是可以正常执行的
     * - 支持GraphQL执行引擎的高效解析器查找
     * 
     * 返回结果特点：
     * - 结果集已经过双重过滤，可直接用于GraphQL执行
     * - 按数据库默认顺序返回，可根据需要添加ORDER BY子句
     * - 空结果表示该操作类型没有可用的解析器配置
     * 
     * @param operationType GraphQL操作类型（Query/Mutation/Subscription）
     * @return 指定操作类型的启用状态解析器配置列表
     */
    @Query("SELECT r FROM ResolverConfig r WHERE r.enabled = true AND r.operationType = ?1")
    List<ResolverConfig> findEnabledByOperationType(String operationType);
}