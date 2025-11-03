# 动态数据服务

这是一个基于 Spring Boot 和 Spring GraphQL 的动态数据服务应用，支持用户通过界面配置和管理 GraphQL resolver 和对应的 SQL 查询，无需修改代码即可提供灵活的数据 CRUD 能力。

## 功能特性

- 🚀 **动态 GraphQL Resolver**: 通过配置自动生成 GraphQL 查询和变更操作
- 🛠️ **可视化配置管理**: 提供友好的 Web 界面进行 resolver 配置
- 🔒 **SQL 安全验证**: 内置 SQL 注入防护和查询验证
- 📝 **实时测试**: 支持 SQL 和 GraphQL 查询的实时测试
- 🔄 **热更新**: 配置更改后立即生效，无需重启服务
- 📊 **参数化查询**: 支持动态参数绑定

## 技术栈

- **后端**: Spring Boot 3.2, Spring GraphQL, Spring Data JPA
- **数据库**: MySQL 8.0
- **前端**: Bootstrap 5, Vanilla JavaScript
- **构建工具**: Maven

## 快速开始

### 前置条件

- Java 17+
- Maven 3.6+
- MySQL 8.0+

### 1. 数据库准备

确保 MySQL 服务运行，应用会自动创建数据库 `dynamic_data_service`。

### 2. 启动应用

```bash
cd dynamic-data-service
mvn spring-boot:run
```

### 3. 访问应用

- **管理界面**: http://localhost:8080
- **GraphQL Playground**: http://localhost:8080/graphiql
- **REST API**: http://localhost:8080/api/resolver-config

## 使用示例

### 1. 创建一个查询 Resolver

1. 打开管理界面 http://localhost:8080
2. 填写配置信息：
   - **Resolver名称**: `getUserById`
   - **操作类型**: `QUERY`
   - **SQL查询**: `SELECT id, name, email FROM users WHERE id = #{userId}`
   - **输入参数**: `{"userId": "Long"}`
   - **输出字段**: `{"id": "Long", "name": "String", "email": "String"}`

3. 点击"创建配置"

### 2. 测试 GraphQL 查询

在 GraphiQL 中执行：

```graphql
query {
  getUserById(userId: 1) {
    id
    name
    email
  }
}
```

### 3. 创建一个变更 Resolver

配置信息：
- **Resolver名称**: `createUser`
- **操作类型**: `MUTATION`
- **SQL查询**: `INSERT INTO users (name, email) VALUES (#{name}, #{email})`
- **输入参数**: `{"name": "String", "email": "String"}`

## API 文档

### REST API 端点

- `GET /api/resolver-config` - 获取所有配置
- `POST /api/resolver-config` - 创建新配置
- `PUT /api/resolver-config/{id}` - 更新配置
- `DELETE /api/resolver-config/{id}` - 删除配置
- `POST /api/resolver-config/test-sql` - 测试 SQL 查询

### SQL 参数绑定

使用 `#{参数名}` 语法进行参数绑定：

```sql
SELECT * FROM products WHERE price > #{minPrice} AND category = #{category}
```

对应的输入参数配置：

```json
{
  "minPrice": "Double",
  "category": "String"
}
```

## 配置说明

### Resolver 配置字段

- **resolverName**: GraphQL 中使用的字段名，必须唯一
- **operationType**: 操作类型，支持 `QUERY` 和 `MUTATION`
- **sqlQuery**: 要执行的 SQL 语句，支持参数化查询
- **inputParameters**: JSON 格式的输入参数定义
- **outputFields**: JSON 格式的输出字段定义
- **description**: 描述信息
- **enabled**: 是否启用该 resolver

### 安全限制

- 测试功能仅支持 SELECT 查询
- 生产环境建议配置更严格的 SQL 验证规则
- 支持参数化查询防止 SQL 注入

## 数据库连接配置

在 `application.properties` 中配置数据库连接：

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/dynamic_data_service?createDatabaseIfNotExist=true
spring.datasource.username=root
spring.datasource.password=123456
```

## 故障排除

### 常见问题

1. **数据库连接失败**
   - 检查 MySQL 服务是否运行
   - 验证数据库连接信息

2. **GraphQL 查询失败**
   - 确认 resolver 配置已启用
   - 检查 SQL 语法和参数绑定

3. **参数绑定错误**
   - 确保参数名称与 SQL 中的占位符一致
   - 检查输入参数 JSON 格式是否正确

## 开发和扩展

### 添加新功能

1. 扩展 `ResolverConfig` 实体
2. 修改相应的 Service 和 Controller
3. 更新前端界面

### 自定义验证规则

修改 `DynamicSqlExecutor.validateSql()` 方法添加自定义验证逻辑。

## 许可证

此项目基于 MIT 许可证开源。