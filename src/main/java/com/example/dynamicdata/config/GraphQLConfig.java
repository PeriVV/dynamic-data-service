package com.example.dynamicdata.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

import com.example.dynamicdata.entity.ResolverConfig;
import com.example.dynamicdata.service.DynamicSchemaService;
import com.example.dynamicdata.service.DynamicSqlExecutor;
import com.example.dynamicdata.service.ResolverConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;

import graphql.schema.DataFetcher;
import graphql.schema.idl.RuntimeWiring;

/**
 * GraphQL配置类
 * 支持动态SQL路由（查询走主库，变更走沙箱库）
 */
@Configuration
public class GraphQLConfig {

    private static final Logger logger = LoggerFactory.getLogger(GraphQLConfig.class);

    @Autowired
    private ResolverConfigService configService;

    @Autowired
    private DynamicSqlExecutor sqlExecutor;

    @Autowired
    private DynamicSchemaService dynamicSchemaService;

    @Bean
    public RuntimeWiringConfigurer runtimeWiringConfigurer() {
        return wiringBuilder -> buildDynamicResolvers(wiringBuilder);
    }

    /** 动态构建所有resolver */
    public void buildDynamicResolvers(RuntimeWiring.Builder wiringBuilder) {
        List<ResolverConfig> configs = configService.getEnabledConfigs();

        for (ResolverConfig config : configs) {
            DataFetcher<Object> df = createDataFetcher(config);
            if ("QUERY".equalsIgnoreCase(config.getOperationType())) {
                wiringBuilder.type("Query",
                        typeBuilder -> typeBuilder.dataFetcher(config.getResolverName(), df));
            } else if ("MUTATION".equalsIgnoreCase(config.getOperationType())) {
                wiringBuilder.type("Mutation",
                        typeBuilder -> typeBuilder.dataFetcher(config.getResolverName(), df));
            }
        }

        // 附加内置dataFetcher
        wiringBuilder.type("Query", builder ->
                builder.dataFetcher("dynamicQuery", createDynamicQueryDataFetcher())
                        .dataFetcher("health", env -> "GraphQL endpoint is healthy and working!"));
    }

    /** 核心：自动判断 SQL 类型 -> 主库 / 沙箱 */
    private DataFetcher<Object> createDataFetcher(ResolverConfig config) {
        return environment -> {
            Map<String, Object> arguments = environment.getArguments();
            Map<String, Object> parameters = extractParameters(config.getInputParameters(), arguments);
            String sql = config.getSqlQuery();

            logger.debug("GraphQL参数: {}", arguments);
            logger.debug("提取的参数: {}", parameters);
            logger.debug("SQL查询: {}", sql);

            try {
                if (sql == null || sql.trim().isEmpty()) {
                    throw new IllegalArgumentException("SQL 为空");
                }

                // 判断SQL类型
                String lower = sql.trim().toLowerCase();
                boolean isSelect = lower.startsWith("select");
                boolean useSandbox = !isSelect; // 增删改走沙箱

                if (isSelect) {
                    List<Map<String, Object>> results = sqlExecutor.executeQuery(sql, parameters, useSandbox);
                    logger.info("GraphQL 查询 -> 数据库: {}", useSandbox ? "sandbox" : "main");

                    if (config.getResolverName().contains("ById") ||
                            config.getResolverName().contains("ByName") ||
                            config.getResolverName().contains("ByEmail")) {
                        return results.isEmpty() ? null : results.get(0);
                    }
                    return results;

                } else {
                    int affected = sqlExecutor.executeUpdate(sql, parameters, useSandbox);
                    logger.info("GraphQL 变更 -> 数据库: {}", useSandbox ? "sandbox" : "main");

                    Map<String, Object> result = new HashMap<>();
                    result.put("affectedRows", affected);
                    result.put("database", useSandbox ? "sandbox" : "main");
                    result.put("success", true);
                    return result;
                }

            } catch (Exception e) {
                logger.error("GraphQL resolver 执行失败: {}", e.getMessage(), e);
                Map<String, Object> err = new HashMap<>();
                err.put("success", false);
                err.put("error", e.getMessage());
                return err;
            }
        };
    }

    /** 支持手动SQL执行的 dataFetcher */
    private DataFetcher<Object> createDynamicQueryDataFetcher() {
        return environment -> {
            String sql = environment.getArgument("sql");
            if (sql == null || sql.trim().isEmpty()) {
                throw new IllegalArgumentException("SQL query cannot be empty");
            }

            String lower = sql.trim().toLowerCase();
            boolean isSelect = lower.startsWith("select");
            boolean useSandbox = !isSelect;

            logger.info("DynamicQuery -> SQL: [{}] -> {}", sql, useSandbox ? "sandbox" : "main");

            if (isSelect) {
                List<Map<String, Object>> res = sqlExecutor.executeQuery(sql, Map.of(), useSandbox);
                return new ObjectMapper().writeValueAsString(res);
            } else {
                int affected = sqlExecutor.executeUpdate(sql, Map.of(), useSandbox);
                Map<String, Object> r = new HashMap<>();
                r.put("success", true);
                r.put("database", useSandbox ? "sandbox" : "main");
                r.put("affectedRows", affected);
                return r;
            }
        };
    }

    /** 参数提取工具 */
    private Map<String, Object> extractParameters(String inputParametersJson, Map<String, Object> arguments) {
        if (inputParametersJson == null || inputParametersJson.trim().isEmpty()) {
            return arguments != null ? arguments : new HashMap<>();
        }
        try {
            Map<String, Object> defs = sqlExecutor.parseParameters(inputParametersJson);
            Map<String, Object> params = new HashMap<>();
            for (String k : defs.keySet()) {
                if (arguments != null && arguments.containsKey(k)) {
                    params.put(k, arguments.get(k));
                }
            }
            return params;
        } catch (Exception e) {
            return arguments != null ? arguments : new HashMap<>();
        }
    }
}
