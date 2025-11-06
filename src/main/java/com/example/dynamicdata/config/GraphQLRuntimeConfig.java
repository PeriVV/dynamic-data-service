package com.example.dynamicdata.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.example.dynamicdata.service.DynamicSchemaService;
import com.example.dynamicdata.service.RefreshableGraphQlService;
import graphql.schema.GraphQLSchema;

@Configuration
public class GraphQLRuntimeConfig {

    @Bean
    public RefreshableGraphQlService graphQlService(DynamicSchemaService dynamicSchemaService) {
        GraphQLSchema initial = dynamicSchemaService.buildSchemaFromConfigs();
        RefreshableGraphQlService svc = new RefreshableGraphQlService(initial);
        dynamicSchemaService.attach(svc);
        return svc; // Spring GraphQL 会自动把它当作 ExecutionGraphQlService，继续走 /graphql
    }
}
