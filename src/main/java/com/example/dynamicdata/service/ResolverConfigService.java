package com.example.dynamicdata.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.dynamicdata.entity.ResolverConfig;
import com.example.dynamicdata.repository.ResolverConfigRepository;

/**
 * GraphQL解析器配置服务类
 * 
 * 该服务类负责管理GraphQL解析器的配置信息，包括：
 * - 解析器配置的增删改查操作
 * - 根据不同条件筛选配置（启用状态、操作类型等）
 * - 提供配置存在性检查功能
 * 
 * 主要功能：
 * 1. 配置管理：支持保存、删除、查询解析器配置
 * 2. 条件查询：支持按ID、解析器名称、操作类型等条件查询
 * 3. 状态筛选：支持查询启用/禁用状态的配置
 * 4. 业务验证：提供配置重复性检查
 * 
 * @author Dynamic Data Service Team
 * @version 1.0
 * @since 2024-01-15
 */
@Service
public class ResolverConfigService {

    /**
     * 解析器配置数据访问层
     * 通过Spring依赖注入自动装配
     */
    @Autowired
    private ResolverConfigRepository repository;

    /**
     * 获取所有解析器配置
     * 
     * @return 所有解析器配置列表
     */
    public List<ResolverConfig> getAllConfigs() {
        return repository.findAll();
    }

    /**
     * 获取所有启用状态的解析器配置
     * 
     * @return 启用状态的解析器配置列表
     */
    public List<ResolverConfig> getEnabledConfigs() {
        return repository.findByEnabledTrue();
    }

    /**
     * 根据ID获取解析器配置
     * 
     * @param id 配置ID
     * @return Optional包装的解析器配置，如果不存在则为空
     */
    public Optional<ResolverConfig> getConfigById(Long id) {
        return repository.findById(id);
    }

    /**
     * 根据解析器名称获取配置
     * 
     * @param resolverName 解析器名称
     * @return Optional包装的解析器配置，如果不存在则为空
     */
    public Optional<ResolverConfig> getConfigByResolverName(String resolverName) {
        return repository.findByResolverName(resolverName);
    }

    /**
     * 根据操作类型获取解析器配置列表
     * 
     * @param operationType 操作类型（如：Query、Mutation、Subscription）
     * @return 指定操作类型的解析器配置列表
     */
    public List<ResolverConfig> getConfigsByOperationType(String operationType) {
        return repository.findByOperationType(operationType);
    }

    /**
     * 根据操作类型获取启用状态的解析器配置列表
     * 
     * @param operationType 操作类型（如：Query、Mutation、Subscription）
     * @return 指定操作类型且启用状态的解析器配置列表
     */
    public List<ResolverConfig> getEnabledConfigsByOperationType(String operationType) {
        return repository.findEnabledByOperationType(operationType);
    }

    /**
     * 保存解析器配置
     * 支持新增和更新操作：
     * - 如果配置ID为null，则执行新增操作
     * - 如果配置ID不为null且存在，则执行更新操作
     * 
     * @param config 要保存的解析器配置对象
     * @return 保存后的解析器配置对象（包含生成的ID）
     */
    public ResolverConfig saveConfig(ResolverConfig config) {
        return repository.save(config);
    }

    /**
     * 根据ID删除解析器配置
     * 
     * @param id 要删除的配置ID
     */
    public void deleteConfig(Long id) {
        repository.deleteById(id);
    }

    /**
     * 检查指定解析器名称的配置是否存在
     * 用于防止重复创建同名解析器配置
     * 
     * @param resolverName 解析器名称
     * @return true表示存在，false表示不存在
     */
    public boolean existsByResolverName(String resolverName) {
        return repository.findByResolverName(resolverName).isPresent();
    }
}