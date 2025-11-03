package com.example.dynamicdata.resolver;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import com.example.dynamicdata.entity.ResolverConfig;
import com.example.dynamicdata.service.DynamicSqlExecutor;
import com.example.dynamicdata.service.ResolverConfigService;

import graphql.schema.DataFetchingEnvironment;

/**
 * 动态数据解析器
 * 负责处理GraphQL查询的数据解析和SQL执行
 * 提供动态的数据查询能力，支持多种数据源和查询类型
 * 
 * 主要功能：
 * 1. 根据字段名称查找对应的解析器配置
 * 2. 执行SQL查询并返回结果
 * 3. 处理查询参数的映射和转换
 * 4. 提供错误处理和异常管理
 * 
 * @author Dynamic Data Service
 * @version 1.0
 * @since 2024-01-01
 */
@Controller
public class DynamicDataResolver {

    private static final Logger logger = LoggerFactory.getLogger(DynamicDataResolver.class);

    /**
     * 解析器配置服务，用于获取和管理解析器配置信息
     */
    @Autowired
    private ResolverConfigService configService;

    /**
     * 动态SQL执行器，负责执行SQL查询和更新操作
     */
    @Autowired
    private DynamicSqlExecutor sqlExecutor;

    /**
     * 动态查询方法，处理GraphQL查询请求
     * 
     * @param environment GraphQL数据获取环境，包含查询字段信息和参数
     * @return 查询结果对象，可能是查询数据、更新结果或错误信息
     * @throws RuntimeException 当解析器未找到或被禁用时抛出异常
     */
    @QueryMapping
    public Object dynamicQuery(DataFetchingEnvironment environment) {
        // 获取GraphQL查询字段名称
        String fieldName = environment.getField().getName();
        // 获取查询参数
        Map<String, Object> arguments = environment.getArguments();

        // 根据字段名称查找对应的解析器配置
        Optional<ResolverConfig> configOpt = configService.getConfigByResolverName(fieldName);
        if (configOpt.isEmpty() || !configOpt.get().getEnabled()) {
            throw new RuntimeException("Resolver '" + fieldName + "' not found or disabled");
        }

        ResolverConfig config = configOpt.get();
        // 提取并处理查询参数
        Map<String, Object> parameters = extractParameters(config.getInputParameters(), arguments);

        try {
            // 根据操作类型执行相应的SQL操作
            if ("QUERY".equals(config.getOperationType())) {
                // 执行查询操作
                return sqlExecutor.executeQuery(config.getSqlQuery(), parameters);
            } else {
                // 执行更新操作（INSERT、UPDATE、DELETE）
                int affected = sqlExecutor.executeUpdate(config.getSqlQuery(), parameters);
                Map<String, Object> result = new HashMap<>();
                result.put("affected", affected);
                result.put("success", true);
                return result;
            }
        } catch (Exception e) {
            // 处理执行异常，返回错误信息
            logger.error("Error occurred while executing dynamic query: {}", e.getMessage(), e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", e.getMessage());
            errorResult.put("success", false);
            return errorResult;
        }
    }

    /**
     * 从GraphQL参数中提取SQL查询所需的参数
     * 
     * @param inputParametersJson 输入参数的JSON定义字符串
     * @param arguments GraphQL查询传入的参数映射
     * @return 提取后的参数映射，用于SQL查询
     */
    private Map<String, Object> extractParameters(String inputParametersJson, Map<String, Object> arguments) {
        // 如果没有定义输入参数，直接返回所有传入的参数
        if (inputParametersJson == null || inputParametersJson.trim().isEmpty()) {
            return arguments != null ? arguments : new HashMap<>();
        }

        try {
            // 解析参数定义JSON
            Map<String, Object> parameterDefinitions = sqlExecutor.parseParameters(inputParametersJson);
            Map<String, Object> parameters = new HashMap<>();

            // 根据参数定义提取对应的参数值
            for (String paramName : parameterDefinitions.keySet()) {
                if (arguments != null && arguments.containsKey(paramName)) {
                    parameters.put(paramName, arguments.get(paramName));
                }
            }

            return parameters;
        } catch (Exception e) {
            // 解析失败时，返回所有传入的参数作为备选方案
            logger.warn("解析输入参数定义失败，使用所有传入参数: {}", e.getMessage());
            return arguments != null ? arguments : new HashMap<>();
        }
    }
}