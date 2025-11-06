package com.example.dynamicdata.config;

import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.dynamicdata.entity.ResolverConfig;
import com.example.dynamicdata.service.DynamicSchemaService;
import com.example.dynamicdata.service.DynamicSqlExecutor;
import com.example.dynamicdata.service.ResolverConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;

import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.*;
import graphql.scalars.ExtendedScalars; // JSON 标量
import org.springframework.graphql.execution.GraphQlSource;

@Configuration
public class GraphQLConfig {

    private static final Logger logger = LoggerFactory.getLogger(GraphQLConfig.class);

    private final ResolverConfigService configService;
    private final DynamicSqlExecutor sqlExecutor;
    private final DynamicSchemaService dynamicSchemaService;

    public GraphQLConfig(ResolverConfigService configService,
                         DynamicSqlExecutor sqlExecutor,
                         DynamicSchemaService dynamicSchemaService) {
        this.configService = configService;
        this.sqlExecutor = sqlExecutor;
        this.dynamicSchemaService = dynamicSchemaService;
    }

    /**
     * 关键：提供一个 GraphQlSource Bean
     * - 动态拼装 SDL（只声明字段名，不写具体字段结构，统一用 JSON 标量兜底）
     * - 动态绑定 DataFetcher
     */
    @Bean
    public GraphQlSource graphQlSource() {
        // 1) 读取数据库里的 resolver 配置
        List<ResolverConfig> configs = configService.getEnabledConfigs();
        if (configs == null) configs = List.of();

        // 2) 生成最小可用 SDL（根类型 + 动态字段）—— 全部返回 JSON
        String sdl = buildSdl(configs);

        // 3) 解析 SDL
        TypeDefinitionRegistry registry = new SchemaParser().parse(sdl);

        // 4) 绑定 JSON 标量 + 动态 DataFetcher
        RuntimeWiring.Builder wiringBuilder = RuntimeWiring.newRuntimeWiring()
                .scalar(ExtendedScalars.Json);

        // 4.1 绑定每个 resolver 的 DataFetcher
        TypeRuntimeWiring.Builder queryType = TypeRuntimeWiring.newTypeWiring("Query");
        TypeRuntimeWiring.Builder mutationType = TypeRuntimeWiring.newTypeWiring("Mutation");

        for (ResolverConfig cfg : configs) {
            DataFetcher<Object> df = createDataFetcher(cfg);
            String op = String.valueOf(cfg.getOperationType()).toUpperCase(Locale.ROOT);
            if ("QUERY".equals(op)) {
                queryType.dataFetcher(cfg.getResolverName(), df);
            } else if ("MUTATION".equals(op)) {
                mutationType.dataFetcher(cfg.getResolverName(), df);
            }
        }

        // 4.2 内置两个基础字段（和你原来一致）
        queryType
                .dataFetcher("dynamicQuery", createDynamicQueryDataFetcher())
                .dataFetcher("health", env -> "GraphQL endpoint is healthy and working!");

        wiringBuilder.type(queryType);
        wiringBuilder.type(mutationType);

        RuntimeWiring wiring = wiringBuilder.build();

        // 5) 生成可执行 Schema 并注册
        GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(registry, wiring);
        return GraphQlSource.builder(schema).build();
    }

    /** —— 下面三个方法保持你原有逻辑 —— */

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

                String lower = sql.trim().toLowerCase();
                boolean isSelect = lower.startsWith("select");
                boolean useSandbox = !isSelect; // 增删改走沙箱

                if (isSelect) {
                    List<Map<String, Object>> results = sqlExecutor.executeQuery(sql, parameters, useSandbox);
                    logger.info("GraphQL 查询 -> 数据库: {}", useSandbox ? "sandbox" : "main");
                    // 直接返回 List<Map> 交给 JSON 标量
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
                // 直接返回对象，交给 JSON 标量
                return res;
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

    /** 根据数据库配置，动态拼装最小可用 SDL（所有字段返回 JSON） */
    private String buildSdl(List<ResolverConfig> configs) {
        StringBuilder sb = new StringBuilder();
        sb.append("scalar JSON\n\n");
        sb.append("type Query {\n");
        sb.append("  health: String!\n");
        sb.append("  dynamicQuery(sql: String!): JSON\n");

        for (ResolverConfig c : configs) {
            String name = c.getResolverName();
            String op = String.valueOf(c.getOperationType()).toUpperCase(Locale.ROOT);
            String args = buildArgsLine(c.getInputParameters()); // (id: ID, name: String) 之类
            if ("QUERY".equals(op)) {
                sb.append("  ").append(name).append(args).append(": JSON\n");
            }
        }
        sb.append("}\n\n");

        sb.append("type Mutation {\n");
        boolean hasMutation = false;
        for (ResolverConfig c : configs) {
            String name = c.getResolverName();
            String op = String.valueOf(c.getOperationType()).toUpperCase(Locale.ROOT);
            String args = buildArgsLine(c.getInputParameters());
            if ("MUTATION".equals(op)) {
                hasMutation = true;
                sb.append("  ").append(name).append(args).append(": JSON\n");
            }
        }
        if (!hasMutation) {
            sb.append("  _noop: JSON\n"); // 保证类型存在
        }
        sb.append("}\n");

        String sdl = sb.toString();
        logger.info("动态 SDL 生成成功:\n{}", sdl);
        return sdl;
    }

    /** 把你配置里的 inputParameters(JSON) 转成 SDL 的参数声明；没有就返回空参数列表 */
    private String buildArgsLine(String inputParametersJson) {
        try {
            // 让你原本的解析器先把参数描述取出来（key -> type）
            Map<String, Object> defs = sqlExecutor.parseParameters(inputParametersJson);
            if (defs == null || defs.isEmpty()) return "()";

            List<String> parts = new ArrayList<>();
            for (Map.Entry<String, Object> e : defs.entrySet()) {
                String name = e.getKey();
                String t = String.valueOf(e.getValue());
                // 简单映射：string/number/boolean/id → GraphQL 基本类型
                String gqlType = toGraphQLType(t);
                parts.add(name + ": " + gqlType);
            }
            return "(" + String.join(", ", parts) + ")";
        } catch (Exception ex) {
            return "()";
        }
    }

    private String toGraphQLType(String t) {
        if (t == null) return "JSON";
        String s = t.toLowerCase(Locale.ROOT);
        if (s.contains("int") || s.contains("long")) return "Int";
        if (s.contains("double") || s.contains("float") || s.contains("decimal") || s.contains("number")) return "Float";
        if (s.contains("bool")) return "Boolean";
        if (s.contains("id")) return "ID";
        if (s.contains("string") || s.contains("char") || s.contains("text") || s.contains("varchar")) return "String";
        return "JSON";
    }
}
