package com.example.dynamicdata;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

@Disabled("Manual integration example; disable by default")
@SpringBootTest
public class GraphQLApiTest {

    private static final String GRAPHQL_ENDPOINT = "http://localhost:8081/graphql";

    @Test
    public void testGetUserById() {
        // 1️⃣ 构建GraphQL查询语句
        String query = "query { getUserById(userId: 1) { id name email } }";

        // 2️⃣ 构建请求体（GraphQL用JSON格式）
        String requestBody = "{ \"query\": \"" + query.replace("\"", "\\\"") + "\" }";

        // 3️⃣ 构建HTTP请求
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

        // 4️⃣ 调用GraphQL接口
        ResponseEntity<String> response =
                restTemplate.postForEntity(GRAPHQL_ENDPOINT, request, String.class);

        // 5️⃣ 输出结果
        System.out.println("\n✅ GraphQL 响应内容：");
        System.out.println(response.getBody());
    }
}
