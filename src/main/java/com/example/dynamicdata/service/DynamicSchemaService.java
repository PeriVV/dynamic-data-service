package com.example.dynamicdata.service;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

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
 * - 完全不手写SDL：基于 ResolverConfig 自动生成 Query/Mutation、参数与返回类型
 * - 保存/发布API后，调用 reloadGraphQLSchema() 即可热更新 /graphql
 */
@Service
@Transactional
public class DynamicSchemaService {

    private static final Logger logger = LoggerFactory.getLogger(DynamicSchemaService.class);

    @Autowired private SchemaDefinitionRepository schemaRepository;
    @Autowired private ResolverConfigService     resolverConfigService;
    @Autowired private DynamicSqlExecutor        sqlExecutor;
    // 放在类字段区（比如 currentGraphQL 旁边）
    private RefreshableGraphQlService graphQlService;

    /** 运行中的 GraphQL 引擎实例（热更新时替换） */
    private GraphQL currentGraphQL;
    /** 缓存最近一次生成的 SDL 文本，避免无变化重建 */
    private String  currentSchemaContent;

    // --------------------------- 对外常规CRUD（与你原来一致） ---------------------------

    public List<SchemaDefinition> getAllSchemas() { return schemaRepository.findAll(); }

    public List<SchemaDefinition> getActiveSchemas() { return schemaRepository.findByIsActiveTrue(); }

    public Optional<SchemaDefinition> getSchemaByName(String schemaName) {
        return schemaRepository.findBySchemaName(schemaName);
    }

    public SchemaDefinition createSchema(String schemaName, String schemaContent, String description) {
        if (schemaRepository.existsBySchemaName(schemaName)) {
            throw new RuntimeException("Schema名称已存在: " + schemaName);
        }
        validateSchemaContent(schemaContent);
        SchemaDefinition saved = schemaRepository.save(new SchemaDefinition(schemaName, schemaContent, description));
        logger.info("成功创建Schema定义: {} (ID: {})", schemaName, saved.getId());
        return saved;
    }

    public SchemaDefinition updateSchema(Long id, String schemaContent, String description) {
        SchemaDefinition schema = schemaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Schema不存在: " + id));
        validateSchemaContent(schemaContent);
        String oldVersion = String.valueOf(schema.getVersion());
        schema.setSchemaContent(schemaContent);
        schema.setDescription(description);
        schema.setVersion(schema.getVersion() + 1);
        SchemaDefinition updated = schemaRepository.save(schema);
        logger.info("成功更新Schema定义: {} (版本: {} -> {})", schema.getSchemaName(), oldVersion, updated.getVersion());
        return updated;
    }

    public void activateSchema(Long id) {
        SchemaDefinition schema = schemaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Schema不存在: " + id));
        for (SchemaDefinition s : schemaRepository.findByIsActiveTrue()) {
            s.setIsActive(false);
            schemaRepository.save(s);
        }
        schema.setIsActive(true);
        schemaRepository.save(schema);
        logger.info("成功激活Schema: {} (版本: {})", schema.getSchemaName(), schema.getVersion());
        reloadGraphQLSchema();
    }

    public void deactivateSchema(Long id) {
        SchemaDefinition schema = schemaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Schema不存在: " + id));
        schema.setIsActive(false);
        schemaRepository.save(schema);
        logger.info("成功停用Schema: {}", schema.getSchemaName());
        reloadGraphQLSchema();
    }

    public void deleteSchema(Long id) {
        SchemaDefinition schema = schemaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Schema不存在: " + id));
        if (Boolean.TRUE.equals(schema.getIsActive())) {
            throw new RuntimeException("无法删除激活的Schema，请先停用");
        }
        schemaRepository.delete(schema);
        logger.info("成功删除Schema定义: {}", schema.getSchemaName());
    }

    // --------------------------- “不手写SDL”的关键：自动生成并热更新 ---------------------------

    /**
     * 重新生成并热替换 GraphQL Schema（基于 ResolverConfig）
     * - SELECT ById/ByCode/ByName -> 单对象；其它 SELECT -> [对象]
     * - MUTATION -> MutationResult { success, affected }
     */
    public void reloadGraphQLSchema() {
        try {
            // 1) 基于 resolver 配置重建 Schema
            GraphQLSchema newSchema = buildSchemaFromConfigs();

            // 2) 如果有挂载 RefreshableGraphQlService，就热更新它（用于 /graphql 端点）
            if (this.graphQlService != null) {
                this.graphQlService.reload(newSchema);
                logger.info("GraphQL Schema 已热更新并应用到 /graphql 端点");
            } else {
                // 否则保留你原有的本地 GraphQL 实例（以兼容你之前的用法）
                this.currentGraphQL = GraphQL.newGraphQL(newSchema).build();
                logger.info("GraphQL Schema 已重新加载（本地实例更新）");
            }

        } catch (Exception e) {
            logger.error("重新加载 GraphQL Schema 失败: {}", e.getMessage(), e);
            throw new RuntimeException("Schema 重新加载失败: " + e.getMessage(), e);
        }
    }


    /** 提供给 /graphql 端点使用（首次访问会触发生成） */
    public GraphQL getCurrentGraphQL() {
        if (currentGraphQL == null) {
            reloadGraphQLSchema();
        }
        return currentGraphQL;
    }

    public String getCurrentSchemaContent() { return currentSchemaContent; }

    // --------------------------- 生成 SDL：核心逻辑 ---------------------------

    /** 把 ResolverConfig 转成 SDL；完全不需要手写 schema */
    private String generateDynamicSchema() {
        StringBuilder sdl = new StringBuilder();

        // 标量：标准标量（ID、String、Int...）在 graphql-java 内置，无需声明
        // 如需 JSON，可解开下一行：但我们下面不用 JSON 作为返回，全部生成具体类型
        // sdl.append("scalar JSON\n\n");

        List<com.example.dynamicdata.entity.ResolverConfig> all = resolverConfigService.getEnabledConfigs();
        List<com.example.dynamicdata.entity.ResolverConfig> queries =
                all.stream().filter(rc -> "QUERY".equalsIgnoreCase(rc.getOperationType())).toList();
        List<com.example.dynamicdata.entity.ResolverConfig> mutations =
                all.stream().filter(rc -> "MUTATION".equalsIgnoreCase(rc.getOperationType())).toList();

        // --- 1) 先生成所有“专属返回类型” ---
        // 规则：每个 resolver 生成一个 Result 类型（避免你手写 User、Order 等）
        // 字段来自 output_fields JSON；没有则默认给一个 { raw: String }（防止前端无字段时报错）
        Set<String> definedTypeNames = new HashSet<>();
        for (com.example.dynamicdata.entity.ResolverConfig rc : all) {
            String typeName = resultTypeName(rc.getResolverName());
            if (definedTypeNames.add(typeName)) {
                Map<String, String> fields = parseOutputFields(rc.getOutputFields());
                if (fields.isEmpty()) {
                    fields = Map.of("raw", "String");
                }
                sdl.append("type ").append(typeName).append(" {\n");
                for (Map.Entry<String, String> f : fields.entrySet()) {
                    // 将不规范类型兜底为 String
                    String gqlType = normalizeScalar(f.getValue());
                    sdl.append("  ").append(f.getKey()).append(": ").append(gqlType).append("\n");
                }
                sdl.append("}\n\n");
            }
        }

        // 变更统一返回
        if (!mutations.isEmpty()) {
            sdl.append("type MutationResult {\n")
                    .append("  success: Boolean!\n")
                    .append("  affected: Int\n")
                    .append("}\n\n");
        }

        // --- 2) 生成 Query 根类型 ---
        sdl.append("type Query {\n");
        if (queries.isEmpty()) {
            sdl.append("  _empty: String\n");
        } else {
            for (com.example.dynamicdata.entity.ResolverConfig rc : queries) {
                String field = buildFieldSDL(rc, /*isMutation*/ false);
                sdl.append("  ").append(field).append("\n");
            }
        }
        sdl.append("}\n\n");

        // --- 3) 生成 Mutation 根类型 ---
        if (!mutations.isEmpty()) {
            sdl.append("type Mutation {\n");
            for (com.example.dynamicdata.entity.ResolverConfig rc : mutations) {
                String field = buildFieldSDL(rc, /*isMutation*/ true);
                sdl.append("  ").append(field).append("\n");
            }
            sdl.append("}\n\n");
        }

        // --- 4) schema 根（可省略，graphql-java 会默认 Query）---
        // 这里显式写上，便于某些工具识别：
        sdl.append("schema { query: Query");
        if (!mutations.isEmpty()) sdl.append(", mutation: Mutation");
        sdl.append(" }\n");

        return sdl.toString();
    }

    /** 构造根字段 SDL：resolverName(参数...): 返回类型 / [返回类型] */
    private String buildFieldSDL(com.example.dynamicdata.entity.ResolverConfig rc, boolean isMutation) {
        String name   = rc.getResolverName();
        String args   = buildArgsSDL(rc.getInputParameters());
        String rType;

        if (!isMutation) {
            // SELECT：ById/ByCode/ByName -> 单对象；其他 -> 列表
            String base = resultTypeName(name);
            boolean single = name.matches(".*(ById|ByCode|ByName)$");
            rType = single ? base : "[" + base + "]";
        } else {
            rType = "MutationResult!";
        }

        return name + "(" + args + "): " + rType;
    }

    /** 参数 SDL：基于 input_parameters JSON（name->GraphQLType）。为空就不给参数。 */
    private String buildArgsSDL(String inputParametersJson) {
        Map<String, Object> defs = Collections.emptyMap();
        try {
            defs = sqlExecutor.parseParameters(inputParametersJson);
        } catch (Exception ignore) {}

        if (defs == null || defs.isEmpty()) return "";

        // 允许 value 是 {type:String, required:Boolean} 或 直接 "String"
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Object> e : defs.entrySet()) {
            String name = e.getKey();
            String type = "String";
            boolean required = false;

            Object v = e.getValue();
            if (v instanceof Map<?,?> m) {
                Object t = m.get("type");
                if (t != null) type = String.valueOf(t);
                Object req = m.get("required");
                if (req != null) required = Boolean.parseBoolean(String.valueOf(req));
            } else if (v != null) {
                type = String.valueOf(v);
            }

            type = normalizeScalar(type);
            parts.add(name + ": " + type + (required ? "!" : ""));
        }
        return String.join(", ", parts);
    }

    /** 将不规范的标量名兜底为合法的 GraphQL 标量 */
    private String normalizeScalar(String raw) {
        if (raw == null || raw.isBlank()) return "String";
        String t = raw.trim();
        // 常见别名统一
        if (t.equalsIgnoreCase("integer") || t.equalsIgnoreCase("long")) return "Int";
        if (t.equalsIgnoreCase("bool")) return "Boolean";
        if (t.equalsIgnoreCase("float32") || t.equalsIgnoreCase("float64") || t.equalsIgnoreCase("double")) return "Float";
        if (t.equalsIgnoreCase("id")) return "ID";
        if (t.equalsIgnoreCase("string") || t.equalsIgnoreCase("ID")
                || t.equalsIgnoreCase("Int") || t.equalsIgnoreCase("Float")
                || t.equalsIgnoreCase("Boolean")) return Character.toUpperCase(t.charAt(0)) + t.substring(1).toLowerCase();
        // 其它一律当成 String（避免 UnknownType）
        return "String";
    }

    /** 解析 output_fields：支持两种格式
     *  1) [{"name":"id","type":"ID"}, {"name":"email","type":"String"}]
     *  2) {"id":"ID","email":"String"}
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> parseOutputFields(String json) {
        try {
            if (json == null || json.isBlank()) return Collections.emptyMap();
            Object obj = sqlExecutor.parseParameters(json);
            if (obj instanceof Map<?, ?> m) {
                Map<String, String> out = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    out.put(String.valueOf(e.getKey()), normalizeScalar(String.valueOf(e.getValue())));
                }
                return out;
            } else if (obj instanceof List<?> list) {
                Map<String, String> out = new LinkedHashMap<>();
                for (Object o : list) {
                    if (o instanceof Map<?, ?> one) {
                        Object n = one.get("name");
                        Object t = one.get("type");
                        if (n != null) out.put(String.valueOf(n), normalizeScalar(t == null ? "String" : String.valueOf(t)));
                    }
                }
                return out;
            }
        } catch (Exception ignore) {}
        return Collections.emptyMap();
    }

    /** 生成“专属返回类型名”：ResolverName_Result（首字母大写） */
    private String resultTypeName(String resolverName) {
        if (resolverName == null || resolverName.isBlank()) return "Result";
        String base = resolverName.substring(0, 1).toUpperCase() + resolverName.substring(1);
        return base + "_Result";
    }

    // --------------------------- RuntimeWiring（把 resolver 绑上） ---------------------------

    private RuntimeWiring buildRuntimeWiring() {
        RuntimeWiring.Builder wiring = RuntimeWiring.newRuntimeWiring();

        // --- 1) 兜底：无论有没有配置，都提供可用的基础查询 ---
        // 注意：你的 SDL 里需要有对应字段（例如 _ping / health）
        wiring.type("Query", typeWiring -> typeWiring
                .dataFetcher("_ping", env -> "pong")
                .dataFetcher("health", env -> "OK")
        );

        // 如果你的 SDL 里写了 `scalar JSON`，就把扩展标量注册上（没写就别加）
        // wiring.scalar(ExtendedScalars.Json);

        // --- 2) 动态解析器（保持你原来的逻辑） ---
        List<com.example.dynamicdata.entity.ResolverConfig> configs = resolverConfigService.getEnabledConfigs();
        for (com.example.dynamicdata.entity.ResolverConfig c : configs) {
            DataFetcher<Object> df = createDataFetcher(c);
            if ("QUERY".equalsIgnoreCase(c.getOperationType())) {
                wiring.type("Query", tb -> tb.dataFetcher(c.getResolverName(), df));
            } else if ("MUTATION".equalsIgnoreCase(c.getOperationType())) {
                wiring.type("Mutation", tb -> tb.dataFetcher(c.getResolverName(), df));
            }
        }

        logger.info("Dynamic resolvers built successfully. Total configs: {}", configs.size());
        return wiring.build();
    }

    private DataFetcher<Object> createDataFetcher(com.example.dynamicdata.entity.ResolverConfig config) {
        return env -> {
            Map<String, Object> args = env.getArguments();
            String sql = config.getSqlQuery();
            // ★ 从配置里拿数据源类型，默认 MYSQL
            String ds = Optional.ofNullable(config.getDataSource())
                    .map(String::trim)
                    .map(String::toUpperCase)
                    .orElse("MYSQL");   // MYSQL / DM8 / POSTGRESQL

            if (sql == null || sql.isBlank()) {
                throw new RuntimeException("SQL为空: " + config.getResolverName());
            }

            boolean isSelect = sql.trim().toLowerCase().startsWith("select");
            boolean useSandbox = !isSelect;  // 查询走主库，写操作走 sandbox

            try {
                if ("QUERY".equalsIgnoreCase(config.getOperationType())) {
                    // ★ 用带 dataSourceKind + useSandbox 的重载
                    List<Map<String, Object>> rows =
                            sqlExecutor.executeQuery(sql, args, ds, useSandbox);

                    boolean single = config.getResolverName() != null
                            && config.getResolverName().matches(".*(ById|ByCode|ByName)$");

                    return single ? (rows.isEmpty() ? null : rows.get(0)) : rows;

                } else if ("MUTATION".equalsIgnoreCase(config.getOperationType())) {
                    int affected =
                            sqlExecutor.executeUpdate(sql, args, ds, useSandbox);

                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("success", affected > 0);
                    r.put("affected", affected);
                    return r;
                }

                throw new RuntimeException("Unsupported operation type: " + config.getOperationType());
            } catch (Exception e) {
                // ★ 包一层，GraphQL 里会显示 “Error executing SQL: xxx”
                throw new RuntimeException("Error executing SQL: " + e.getMessage(), e);
            }
        };
    }


    // --------------------------- 其它工具/校验 ---------------------------

    private void validateSchemaContent(String schemaContent) {
        try {
            SchemaParser sp = new SchemaParser();
            TypeDefinitionRegistry reg = sp.parse(schemaContent);
            logger.debug("Schema语法验证通过，类型数量: {}", reg.types().size());
        } catch (Exception e) {
            throw new RuntimeException("GraphQL Schema语法错误: " + e.getMessage(), e);
        }
    }

    public String mergeSchemaContents(List<String> schemaContents) {
        if (schemaContents == null || schemaContents.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        AtomicInteger cnt = new AtomicInteger(0);
        for (String s : schemaContents) {
            if (s != null && !s.isBlank()) {
                sb.append(s);
                if (!s.endsWith("\n")) sb.append("\n");
                cnt.incrementAndGet();
            }
        }
        logger.debug("合并 {} 段SDL", cnt.get());
        return sb.toString();
    }
    // 让外部把 RefreshableGraphQlService 注入进来，便于热更新时调用 reload()
    public void attach(RefreshableGraphQlService svc) {
        this.graphQlService = svc;
    }
    // 启动时或需要时，基于当前数据库的 resolver 配置，生成 GraphQLSchema
    public GraphQLSchema buildSchemaFromConfigs() {
        try {
            // 1) 生成 SDL（你已有的方法）
            String sdl = generateDynamicSchema();

            // 2) 解析 SDL -> TypeDefinitionRegistry
            SchemaParser parser = new SchemaParser();
            TypeDefinitionRegistry typeRegistry = parser.parse(sdl);

            // 3) 生成 RuntimeWiring（你已有的方法）
            RuntimeWiring wiring = buildRuntimeWiring();

            // 4) 生成可执行 Schema
            SchemaGenerator generator = new SchemaGenerator();
            GraphQLSchema executable = generator.makeExecutableSchema(typeRegistry, wiring);

            // 记录一下当前内存中的 sdl，便于后续是否变更的判断
            this.currentSchemaContent = sdl;
            return executable;
        } catch (Exception e) {
            logger.error("根据配置构建 GraphQLSchema 失败", e);
            throw new RuntimeException("构建 GraphQLSchema 失败: " + e.getMessage(), e);
        }
    }

}
