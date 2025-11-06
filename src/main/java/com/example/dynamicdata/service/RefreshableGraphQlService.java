package com.example.dynamicdata.service;

import java.util.concurrent.atomic.AtomicReference;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import org.springframework.graphql.ExecutionGraphQlRequest;
import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.ExecutionGraphQlService;
import reactor.core.publisher.Mono;

/** 可热更新的 GraphQL 执行服务（兼容不同 spring-graphql 版本的响应构造） */
public class RefreshableGraphQlService implements ExecutionGraphQlService {

    private final AtomicReference<GraphQL> graphQLRef = new AtomicReference<>();

    public RefreshableGraphQlService(GraphQLSchema initialSchema) {
        this.graphQLRef.set(GraphQL.newGraphQL(initialSchema).build());
    }

    /** 热更新 Schema */
    public void reload(GraphQLSchema newSchema) {
        this.graphQLRef.set(GraphQL.newGraphQL(newSchema).build());
    }

    @Override
    public Mono<ExecutionGraphQlResponse> execute(ExecutionGraphQlRequest request) {
        GraphQL graphQL = this.graphQLRef.get();
        return Mono.fromCallable(() -> {
            ExecutionInput executionInput = request.toExecutionInput();
            ExecutionResult result = graphQL.execute(executionInput);

            // 通过工厂方法，自动选择与你当前依赖匹配的构造器
            return GraphQlResponseFactory.build(request, executionInput, result);
        });
    }
}
