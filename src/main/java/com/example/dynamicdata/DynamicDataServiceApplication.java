package com.example.dynamicdata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * 动态数据服务应用程序主类
 * 
 * 功能说明：
 * - 提供基于GraphQL的动态数据查询服务
 * - 支持通过配置文件动态定义数据查询解析器
 * - 集成MySQL数据库进行数据存储和查询
 * - 自动加载.env环境变量配置文件
 * 
 * 技术栈：
 * - Spring Boot 3.2.0
 * - Spring Data JPA
 * - GraphQL
 * - MySQL 8.x
 * - dotenv-java（环境变量管理）
 * 
 * 启动端口：8081
 * GraphQL端点：/graphql
 * GraphiQL界面：/graphiql
 * 
 * @author Dynamic Data Service Team
 * @version 1.0.0
 * @since 2024
 */
@SpringBootApplication
public class DynamicDataServiceApplication {

    /**
     * 应用程序入口方法
     * 
     * 执行流程：
     * 1. 加载.env文件中的环境变量配置
     * 2. 将环境变量设置为系统属性，供Spring Boot使用
     * 3. 启动Spring Boot应用程序
     * 
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        // 第一步：加载.env文件中的环境变量
        // 这样可以在不修改系统环境变量的情况下配置数据库连接等敏感信息
        try {
            // 配置dotenv加载器
            Dotenv dotenv = Dotenv.configure()
                    .directory("./")           // 从当前目录加载.env文件
                    .ignoreIfMissing()         // 如果文件不存在则忽略，不抛出异常
                    .load();                   // 执行加载操作
            
            // 第二步：将.env文件中的变量设置为系统属性
            // Spring Boot会自动读取系统属性中的配置值
            dotenv.entries().forEach(entry -> {
                System.setProperty(entry.getKey(), entry.getValue());
                // 可选：打印加载的环境变量（生产环境中应移除敏感信息的打印）
                if (!entry.getKey().toLowerCase().contains("password") && 
                    !entry.getKey().toLowerCase().contains("secret")) {
                    System.out.println("Loaded env var: " + entry.getKey() + "=" + entry.getValue());
                }
            });
            
            System.out.println("Successfully loaded .env file with " + dotenv.entries().size() + " variables");
            
        } catch (Exception e) {
            // 第三步：异常处理 - 即使.env文件加载失败也不影响应用启动
            // 这样可以确保在生产环境中即使没有.env文件也能正常运行
            System.out.println("Warning: Could not load .env file: " + e.getMessage());
            System.out.println("Application will continue with system environment variables or default values");
        }
        
        // 第四步：启动Spring Boot应用程序
        // 此时所有环境变量已经设置完成，Spring Boot会自动使用这些配置
        SpringApplication.run(DynamicDataServiceApplication.class, args);
    }

}