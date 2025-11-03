# 示例数据库脚本

-- 创建测试数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS dynamic_data_service;
USE dynamic_data_service;

-- 创建示例用户表
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    age INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 创建示例产品表
CREATE TABLE IF NOT EXISTS products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    category VARCHAR(100),
    description TEXT,
    in_stock BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建示例订单表
CREATE TABLE IF NOT EXISTS orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    total_amount DECIMAL(10, 2) NOT NULL,
    status VARCHAR(50) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (product_id) REFERENCES products(id)
);

-- 插入示例数据
INSERT INTO users (name, email, age) VALUES
('张三', 'zhangsan@example.com', 25),
('李四', 'lisi@example.com', 30),
('王五', 'wangwu@example.com', 28),
('赵六', 'zhaoliu@example.com', 35);

INSERT INTO products (name, price, category, description, in_stock) VALUES
('笔记本电脑', 5999.00, '电子产品', '高性能办公笔记本', TRUE),
('无线鼠标', 99.00, '电子产品', '人体工学无线鼠标', TRUE),
('办公椅', 899.00, '办公用品', '人体工学办公椅', TRUE),
('咖啡杯', 39.90, '生活用品', '陶瓷咖啡杯', TRUE),
('书籍', 59.00, '图书', 'Spring Boot 实战', FALSE);

INSERT INTO orders (user_id, product_id, quantity, total_amount, status) VALUES
(1, 1, 1, 5999.00, 'COMPLETED'),
(1, 2, 2, 198.00, 'COMPLETED'),
(2, 3, 1, 899.00, 'PENDING'),
(3, 4, 3, 119.70, 'SHIPPED'),
(4, 5, 1, 59.00, 'CANCELLED');

-- 创建schema定义表
CREATE TABLE IF NOT EXISTS schema_definitions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    schema_name VARCHAR(255) NOT NULL UNIQUE,
    schema_content TEXT NOT NULL,
    description TEXT,
    version INT DEFAULT 1,
    is_active BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 插入默认schema定义
INSERT INTO schema_definitions (schema_name, schema_content, description, is_active) VALUES
('default', 'type Query {
    hello: String
    getUserById(id: ID!): User
    dynamicQuery(sql: String!): String
}

type User {
    id: ID!
    name: String!
    email: String!
    createdAt: String
}', '默认GraphQL Schema', true);