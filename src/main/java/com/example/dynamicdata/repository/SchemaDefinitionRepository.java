package com.example.dynamicdata.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.example.dynamicdata.entity.SchemaDefinition;

/**
 * GraphQL Schema定义数据访问层接口
 * 
 * 该接口继承自Spring Data JPA的JpaRepository，提供Schema定义实体的完整数据访问功能。
 * 除了基础的CRUD操作外，还提供了针对Schema管理业务场景的专用查询方法。
 * 
 * 核心功能：
 * 1. Schema定义的基础CRUD操作（继承自JpaRepository）
 * 2. 基于Schema名称的查询和存在性检查
 * 3. 激活状态Schema的查询和管理
 * 4. Schema版本历史的查询和排序
 * 5. 自定义JPQL查询支持
 * 
 * 业务场景：
 * - Schema定义的创建、更新、删除操作
 * - 激活Schema的查询和切换管理
 * - Schema版本控制和历史追踪
 * - Schema名称唯一性验证
 * - 最新激活Schema的快速获取
 * 
 * 技术特性：
 * - 基于Spring Data JPA的声明式数据访问
 * - 支持方法名称约定的自动查询生成
 * - 支持@Query注解的自定义JPQL查询
 * - 自动事务管理和异常处理
 * - 类型安全的查询结果返回
 * 
 * @author Dynamic Data Service Team
 * @version 1.0
 * @since 2024-01-15
 */
@Repository
public interface SchemaDefinitionRepository extends JpaRepository<SchemaDefinition, Long> {
    
    /**
     * 根据Schema名称查找Schema定义
     * 
     * 该方法用于通过Schema名称精确查找对应的Schema定义记录。
     * 由于Schema名称在系统中具有唯一性约束，该方法最多返回一条记录。
     * 
     * 使用场景：
     * - Schema存在性验证
     * - 根据名称获取特定Schema的详细信息
     * - Schema更新前的记录查找
     * - 重复名称检查
     * 
     * @param schemaName Schema名称，不能为null或空字符串
     * @return Optional包装的Schema定义，如果不存在则为空Optional
     */
    Optional<SchemaDefinition> findBySchemaName(String schemaName);
    
    /**
     * 查找所有当前激活状态的Schema定义
     * 
     * 该方法返回系统中所有isActive字段为true的Schema定义记录。
     * 在正常业务场景下，系统应该只有一个激活的Schema，但该方法
     * 支持返回多个记录以处理数据不一致或特殊业务需求的情况。
     * 
     * 使用场景：
     * - 获取当前生效的Schema定义
     * - Schema激活状态的批量检查
     * - 系统启动时的Schema加载
     * - 数据一致性验证（检查是否有多个激活Schema）
     * 
     * @return 当前激活状态的Schema定义列表，如果没有激活Schema则返回空列表
     */
    List<SchemaDefinition> findByIsActiveTrue();
    
    /**
     * 根据Schema名称查找激活状态的Schema定义
     * 
     * 该方法结合了名称查找和激活状态过滤，用于精确定位
     * 特定名称且当前处于激活状态的Schema定义。
     * 
     * 查询条件：
     * - schemaName = 指定的Schema名称
     * - isActive = true
     * 
     * 使用场景：
     * - 验证特定Schema是否为当前激活状态
     * - 获取指定名称的激活Schema详细信息
     * - Schema切换前的状态检查
     * - 业务逻辑中的Schema状态验证
     * 
     * @param schemaName 要查找的Schema名称
     * @return Optional包装的激活状态Schema定义，如果不存在或未激活则为空Optional
     */
    Optional<SchemaDefinition> findBySchemaNameAndIsActiveTrue(String schemaName);
    
    /**
     * 检查指定Schema名称是否已存在
     * 
     * 该方法用于验证Schema名称的唯一性，在创建新Schema前进行重复性检查。
     * 相比于findBySchemaName方法，该方法只返回布尔值，性能更优。
     * 
     * 实现原理：
     * - 基于Spring Data JPA的exists查询
     * - 只检查记录存在性，不返回具体数据
     * - 数据库层面的高效查询优化
     * 
     * 使用场景：
     * - 新Schema创建前的名称唯一性验证
     * - 批量Schema导入时的重复检查
     * - 用户输入验证和提示
     * - 数据完整性约束的业务层实现
     * 
     * @param schemaName 要检查的Schema名称
     * @return true表示名称已存在，false表示名称可用
     */
    boolean existsBySchemaName(String schemaName);
    
    /**
     * 获取最新更新的激活状态Schema定义列表
     * 
     * 该方法使用自定义JPQL查询，按照更新时间倒序返回所有激活状态的Schema定义。
     * 查询结果按updatedAt字段降序排列，最近更新的Schema排在前面。
     * 
     * JPQL查询说明：
     * - SELECT s FROM SchemaDefinition s: 查询SchemaDefinition实体
     * - WHERE s.isActive = true: 过滤激活状态的记录
     * - ORDER BY s.updatedAt DESC: 按更新时间降序排列
     * 
     * 使用场景：
     * - 获取最近更新的激活Schema
     * - Schema管理界面的排序显示
     * - 系统启动时优先加载最新Schema
     * - Schema变更历史的时间序列分析
     * 
     * 注意事项：
     * - 正常情况下应该只有一个激活Schema
     * - 如果存在多个激活Schema，返回按时间排序的列表
     * - 可用于检测和修复数据不一致问题
     * 
     * @return 按更新时间降序排列的激活Schema定义列表
     */
    @Query("SELECT s FROM SchemaDefinition s WHERE s.isActive = true ORDER BY s.updatedAt DESC")
    List<SchemaDefinition> findLatestActiveSchemas();
    
    /**
     * 根据Schema名称获取所有版本的历史记录
     * 
     * 该方法使用自定义JPQL查询，返回指定Schema名称的所有版本记录，
     * 按版本号降序排列，最新版本排在前面。
     * 
     * JPQL查询说明：
     * - SELECT s FROM SchemaDefinition s: 查询SchemaDefinition实体
     * - WHERE s.schemaName = ?1: 按Schema名称过滤，?1为第一个参数
     * - ORDER BY s.version DESC: 按版本号降序排列
     * 
     * 版本控制特性：
     * - 支持同一Schema名称的多版本管理
     * - 版本号自动递增，确保版本历史的完整性
     * - 提供完整的Schema演进历史追踪
     * 
     * 使用场景：
     * - Schema版本历史查看和管理
     * - Schema回滚操作的版本选择
     * - 版本比较和差异分析
     * - 审计和合规性检查
     * - 开发过程中的版本追踪
     * 
     * 返回数据特点：
     * - 包含该Schema名称的所有历史版本
     * - 按版本号从高到低排序（最新版本在前）
     * - 包括激活和非激活状态的所有版本
     * 
     * @param schemaName 要查询版本历史的Schema名称
     * @return 按版本号降序排列的Schema定义版本列表，如果Schema不存在则返回空列表
     */
    @Query("SELECT s FROM SchemaDefinition s WHERE s.schemaName = ?1 ORDER BY s.version DESC")
    List<SchemaDefinition> findAllVersionsBySchemaName(String schemaName);
}