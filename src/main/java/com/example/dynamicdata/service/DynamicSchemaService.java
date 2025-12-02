package com.example.dynamicdata.service;

import com.example.dynamicdata.entity.ResolverConfig;
import com.example.dynamicdata.entity.SchemaDefinition;
import com.example.dynamicdata.repository.SchemaDefinitionRepository;
import graphql.GraphQL;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.*;
import graphql.scalars.ExtendedScalars;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 动态 GraphQL Schema 管理（改造版）：
 *
 * 1. Schema 不再由 resolver_config 动态生成 SDL，而是完全使用「手写/配置」的 SDL（存储在 SchemaDefinition 表中，激活的那一份）。
 * 2. resolver_config 只负责：
 *    - 提供 SQL
 *    - 提供 dataSource
 *    - 由本类创建对应的 DataFetcher，并绑定到你手写的 schema 中的字段
 * 3. 因此：
 *    - 你在 schemaContent 里可以自由写：User / Order / UserOrder / JSON 等强类型
 *    - 例如 getUserOrders 可以返回 [UserOrder!]!，不再被强制改成 JSON
 */
@Service
@Transactional
public class DynamicSchemaService {

    private static final Logger log = LoggerFactory.getLogger(DynamicSchemaService.class);

    private final SchemaDefinitionRepository schemaRepository;
    private final ResolverConfigService resolverConfigService;
    private final DynamicSqlExecutor sqlExecutor;
    // 当前挂在 /graphql 上的可热更新服务（可选）
    private RefreshableGraphQlService graphQlService;

    /** 当前有效的 GraphQL 实例（供 /graphql 使用） */
    private volatile GraphQL currentGraphQL;
    /** 最近一次生成的 SDL 文本，仅用于调试查看 */
    private volatile String currentSchemaContent = "";

    public DynamicSchemaService(SchemaDefinitionRepository schemaRepository,
                                ResolverConfigService resolverConfigService,
                                DynamicSqlExecutor sqlExecutor) {
        this.schemaRepository = schemaRepository;
        this.resolverConfigService = resolverConfigService;
        this.sqlExecutor = sqlExecutor;
    }

    // =============== 一、对外：SchemaDefinition 的 CRUD（原有功能保留） =================

    public List<SchemaDefinition> getAllSchemas() {
        return schemaRepository.findAll();
    }

    public List<SchemaDefinition> getActiveSchemas() {
        return schemaRepository.findByIsActiveTrue();
    }

    public Optional<SchemaDefinition> getSchemaByName(String schemaName) {
        return schemaRepository.findBySchemaName(schemaName);
    }

    public SchemaDefinition createSchema(String schemaName, String schemaContent, String description) {
        if (schemaRepository.existsBySchemaName(schemaName)) {
            throw new RuntimeException("Schema 名称已存在: " + schemaName);
        }
        validateSchemaContent(schemaContent);
        SchemaDefinition saved = schemaRepository.save(new SchemaDefinition(schemaName, schemaContent, description));
        log.info("创建 Schema 定义成功: {} (id={})", schemaName, saved.getId());
        return saved;
    }

    public SchemaDefinition updateSchema(Long id, String schemaContent, String description) {
        SchemaDefinition schema = schemaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Schema 不存在: " + id));
        validateSchemaContent(schemaContent);
        String oldVersion = String.valueOf(schema.getVersion());
        schema.setSchemaContent(schemaContent);
        schema.setDescription(description);
        schema.setVersion(schema.getVersion() + 1);
        SchemaDefinition updated = schemaRepository.save(schema);
        log.info("更新 Schema 定义成功: {} (version {} -> {})", schema.getSchemaName(), oldVersion, updated.getVersion());
        return updated;
    }

    public void activateSchema(Long id) {
        SchemaDefinition schema = schemaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Schema 不存在: " + id));
        // 标记其它为未激活
        for (SchemaDefinition s : schemaRepository.findByIsActiveTrue()) {
            s.setIsActive(false);
            schemaRepository.save(s);
        }
        schema.setIsActive(true);
        schemaRepository.save(schema);
        log.info("激活 Schema: {} (version={})", schema.getSchemaName(), schema.getVersion());
        // 激活后，重新加载 GraphQLSchema（此时将使用手写 SDL + resolver_config）
        reloadGraphQLSchema();
    }

    public void deactivateSchema(Long id) {
        SchemaDefinition schema = schemaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Schema 不存在: " + id));
        schema.setIsActive(false);
        schemaRepository.save(schema);
        log.info("停用 Schema: {}", schema.getSchemaName());
        reloadGraphQLSchema();
    }

    public void deleteSchema(Long id) {
        SchemaDefinition schema = schemaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Schema 不存在: " + id));
        if (Boolean.TRUE.equals(schema.getIsActive())) {
            throw new RuntimeException("不能删除激活中的 Schema，请先停用");
        }
        schemaRepository.delete(schema);
        log.info("删除 Schema: {}", schema.getSchemaName());
    }

    // =============== 二、给 /graphql 用：动态生成 & 热更新 =================

    /**
     * 重新生成 GraphQLSchema 并替换当前实例
     * - 外部可以在“发布/更新 API 成功后”调用
     */
    public synchronized void reloadGraphQLSchema() {
        GraphQLSchema schema = buildSchemaFromConfigs();
        // 更新本地 GraphQL 实例（如果有地方直接调用 getCurrentGraphQL）
        this.currentGraphQL = GraphQL.newGraphQL(schema).build();

        // 如果有挂着的 RefreshableGraphQlService，就一起刷新 /graphql
        if (this.graphQlService != null) {
            this.graphQlService.reload(schema);
        }

        log.info("GraphQL Schema 已根据手写 SDL + resolver_config 重新生成并加载完成");
    }

    /**
     * /graphql 处理时获取当前的 GraphQL 对象
     */
    public GraphQL getCurrentGraphQL() {
        if (currentGraphQL == null) {
            reloadGraphQLSchema();
        }
        return currentGraphQL;
    }

    public String getCurrentSchemaContent() {
        return currentSchemaContent;
    }

    /**
     * 使用“激活的手写 SDL”+ resolver_config 构建 GraphQLSchema
     */
    public GraphQLSchema buildSchemaFromConfigs() {
        try {
            // 1. 从 resolver_config 获取所有启用的配置（用于绑定 DataFetcher）
            List<ResolverConfig> configs = resolverConfigService.getEnabledConfigs();

            // 2. 从 SchemaDefinition 中获取激活的 schema 内容（可以支持多份，按需合并）
            List<SchemaDefinition> actives = schemaRepository.findByIsActiveTrue();
            if (actives == null || actives.isEmpty()) {
                throw new RuntimeException("没有激活的 GraphQL Schema 定义，请先在前端激活一份 schema");
            }

            List<String> schemaContents = actives.stream()
                    .map(SchemaDefinition::getSchemaContent)
                    .filter(s -> s != null && !s.isBlank())
                    .toList();

            String sdl = mergeSchemaContents(schemaContents);
            this.currentSchemaContent = sdl;

            // 3. 解析你手写/配置好的 SDL
            SchemaParser parser = new SchemaParser();
            TypeDefinitionRegistry registry = parser.parse(sdl);

            // 4. 根据 resolver_config 构建 RuntimeWiring（只负责 DataFetcher / 标量等）
            RuntimeWiring wiring = buildRuntimeWiring(configs);

            SchemaGenerator generator = new SchemaGenerator();
            GraphQLSchema schema = generator.makeExecutableSchema(registry, wiring);
            log.info("GraphQL Schema 构建成功，使用手写 SDL + {} 个 resolver_config", configs.size());
            return schema;
        } catch (Exception e) {
            log.error("根据手写 SDL + resolver_config 构建 GraphQLSchema 失败", e);
            throw new RuntimeException("构建 GraphQLSchema 失败: " + e.getMessage(), e);
        }
    }

    // =============== 三、运行时 wiring：把 resolver 绑到 SQL 执行 =================

    private RuntimeWiring buildRuntimeWiring(List<ResolverConfig> configs) {
        RuntimeWiring.Builder builder = RuntimeWiring.newRuntimeWiring();

        // JSON 标量（依赖 graphql-java-extended-scalars）
        builder.scalar(ExtendedScalars.Json);

        // 固定 health（前提是你的 schema 里有 type Query { health: String! }）
        builder.type("Query", typeWiring ->
                typeWiring.dataFetcher("health", env -> "OK")
        );

        // 动态 Query / Mutation 的 DataFetcher 绑定
        for (ResolverConfig cfg : configs) {
            DataFetcher<Object> df = createDataFetcher(cfg);
            if ("QUERY".equalsIgnoreCase(cfg.getOperationType())) {
                builder.type("Query", tw -> tw.dataFetcher(cfg.getResolverName(), df));
            } else if ("MUTATION".equalsIgnoreCase(cfg.getOperationType())) {
                builder.type("Mutation", tw -> tw.dataFetcher(cfg.getResolverName(), df));
            }
        }

        return builder.build();
    }

    /**
     * 针对单个 resolver 的 DataFetcher
     *  - 自动根据 dataSource 字段选择 MYSQL / DM8
     *  - QUERY 走主库；MUTATION 走 sandbox
     *  - 返回：
     *      - 查询：List<Map<String,Object>> 或单条 Map（ById/ByCode/ByName 结尾的走单条）
     *      - 变更：{ success, affected }
     */
    private DataFetcher<Object> createDataFetcher(ResolverConfig config) {
        return env -> {
            Map<String, Object> args = env.getArguments();
            String sql = config.getSqlQuery();
            String ds = Optional.ofNullable(config.getDataSource())
                    .map(String::trim)
                    .map(String::toUpperCase)
                    .orElse("MYSQL");   // MYSQL / DM8 / POSTGRESQL

            if (sql == null || sql.isBlank()) {
                throw new RuntimeException("SQL 为空: " + config.getResolverName());
            }

            boolean isSelect = sql.trim().toLowerCase().startsWith("select");
            boolean useSandbox = !isSelect; // 查询走主库，写操作走 sandbox

            try {
                if ("QUERY".equalsIgnoreCase(config.getOperationType())) {
                    List<Map<String, Object>> rows =
                            sqlExecutor.executeQuery(sql, args, ds, useSandbox);

                    boolean single = config.getResolverName() != null
                            && config.getResolverName().matches(".*(ById|ByCode|ByName)$");

                    if (single) {
                        return rows.isEmpty() ? null : rows.get(0);
                    } else {
                        return rows;
                    }
                } else if ("MUTATION".equalsIgnoreCase(config.getOperationType())) {
                    int affected =
                            sqlExecutor.executeUpdate(sql, args, ds, useSandbox);

                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("success", affected > 0);
                    r.put("affected", affected);
                    return r;
                }

                throw new RuntimeException("不支持的 operationType: " + config.getOperationType());
            } catch (Exception e) {
                // 统一包装，GraphQL 层能看到 “Error executing SQL: xxx”
                throw new RuntimeException("Error executing SQL: " + e.getMessage(), e);
            }
        };
    }

    // =============== 四、SDL 校验 & 合并（保留你原来的工具方法） =================

    private void validateSchemaContent(String schemaContent) {
        try {
            SchemaParser sp = new SchemaParser();
            TypeDefinitionRegistry reg = sp.parse(schemaContent);
            log.debug("Schema 语法验证通过，类型数量 {}", reg.types().size());
        } catch (Exception e) {
            throw new RuntimeException("GraphQL Schema 语法错误: " + e.getMessage(), e);
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
        log.debug("合并 {} 段 SDL", cnt.get());
        return sb.toString();
    }

    // 供 GraphQLRuntimeConfig 把 RefreshableGraphQlService 传进来
    public void attach(RefreshableGraphQlService svc) {
        this.graphQlService = svc;
    }

}
