package com.example.dynamicdata.service;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import org.springframework.graphql.ExecutionGraphQlRequest;
import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.support.DefaultExecutionGraphQlResponse;

import java.lang.reflect.Constructor;

final class GraphQlResponseFactory {

    private GraphQlResponseFactory() {}

    static ExecutionGraphQlResponse build(ExecutionGraphQlRequest request,
                                          ExecutionInput executionInput,
                                          ExecutionResult result) {
        try {
            // 优先 1：DefaultExecutionGraphQlResponse(ExecutionGraphQlRequest, ExecutionResult)
            try {
                Constructor<DefaultExecutionGraphQlResponse> c1 =
                        DefaultExecutionGraphQlResponse.class.getConstructor(
                                ExecutionGraphQlRequest.class, ExecutionResult.class);
                return c1.newInstance(request, result);
            } catch (NoSuchMethodException ignore) {}

            // 优先 2：DefaultExecutionGraphQlResponse(ExecutionInput, ExecutionResult)
            try {
                Constructor<DefaultExecutionGraphQlResponse> c2 =
                        DefaultExecutionGraphQlResponse.class.getConstructor(
                                ExecutionInput.class, ExecutionResult.class);
                return c2.newInstance(executionInput, result);
            } catch (NoSuchMethodException ignore) {}

            // 兜底 3：DefaultExecutionGraphQlResponse(ExecutionGraphQlRequest, ExecutionInput, ExecutionResult)
            try {
                Constructor<DefaultExecutionGraphQlResponse> c3 =
                        DefaultExecutionGraphQlResponse.class.getConstructor(
                                ExecutionGraphQlRequest.class, ExecutionInput.class, ExecutionResult.class);
                return c3.newInstance(request, executionInput, result);
            } catch (NoSuchMethodException ignore) {}

            throw new IllegalStateException(
                    "No compatible DefaultExecutionGraphQlResponse constructor found. " +
                            "Check spring-graphql version on classpath.");
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException("Failed to construct DefaultExecutionGraphQlResponse via reflection", e);
        }
    }
}
