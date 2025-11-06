# 动态数据服务

这是一个基于 **Spring Boot** 与 **Spring GraphQL** 的动态数据服务应用。  
用户可通过可视化界面配置并发布 GraphQL **查询 (Query)** 与 **变更 (Mutation)** 接口，不需要改代码即可完成数据服务编排。  
✅ 变更默认在**测试数据库（Sandbox）**执行；提供主库与测试库的同步与重置能力。  
✅ 支持 **MySQL** 与 **DM8 (达梦)** 多数据源。

---

## 🚀 功能特性

- **动态 GraphQL Resolver**：配置即生成 `Query` / `Mutation`，热更新生效  
- **步骤式向导 + 工作台**：选择数据源 → 定义接口 → 发布上线  
  - 工作台顶部显示当前数据源与连接状态徽章  
- **可视化管理**：Web 界面支持创建、编辑、删除 Resolver  
- **SQL 安全校验**：自动参数化防注入  
- **即时测试**：
  - SQL 查询即时验证  
  - 内置 GraphiQL 支持 GraphQL 测试  
- **变更沙箱机制**：`Mutation` 默认写入测试库  
- **测试库同步与重置**：可一键同步主库数据到测试库  
- **多数据源支持**：MySQL、DM8  
- **自动参数绑定**：解析 SQL 中的 `#{param}` 自动生成输入参数表单

---

## 🧱 技术栈

| 层级 | 技术 |
|------|------|
| **后端** | Spring Boot 3.2、Spring GraphQL、Spring Data JPA、HikariCP |
| **数据库** | MySQL 8.x、DM8 |
| **前端** | Bootstrap 5、Vanilla JS |
| **构建工具** | Maven |
| **JDK** | Java 21+ |

---

## ⚙️ 快速开始

### 前置条件

- Java 21+
- Maven 3.6+
- MySQL 或 DM8 数据库运行中

### 1️⃣ 配置数据库连接

`application.properties` 示例：

```properties
# ===== MySQL 主库 =====
spring.datasource.url=jdbc:mysql://127.0.0.1:3306/dynamic_data_service?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=123456

# ===== MySQL 测试库 =====
sandbox.datasource.url=jdbc:mysql://127.0.0.1:3306/dynamic_data_service_sandbox?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC
sandbox.datasource.username=root
sandbox.datasource.password=123456

# ===== DM8 主库 =====
dm8.datasource.url=jdbc:dm://127.0.0.1:5236?SCHEMA=SYSDBA&LOGINMODE=4
dm8.datasource.username=SYSDBA
dm8.datasource.password=Abcd12345678
dm8.datasource.driver-class-name=dm.jdbc.driver.DmDriver
dm8.datasource.validation-query=SELECT 1 FROM DUAL

# ===== 服务端口 =====
server.port=8081
```

> 💡 **DM8 提示**  
> - 推荐使用 `127.0.0.1` 而非 `localhost`（避免 IPv6 问题）  
> - 驱动依赖：`com.dameng:DmJdbcDriver18:8.1.2.192`

---

### 2️⃣ 启动应用

```bash
mvn spring-boot:run
```

### 3️⃣ 访问界面

- **管理界面**: http://localhost:8081/
- **GraphiQL**: http://localhost:8081/graphiql
- **API 接口**: http://localhost:8081/api/resolver-config

---

## 💡 使用说明

### 🔹 第 1 步：选择数据源

- 选择 `MySQL` / `DM8` 数据源  
- 卡片徽章自动检测连接状态（已连接 / 未配置 / 失败）  
- 后端接口：
  - `GET /api/datasources/{TYPE}/status`
  - `GET /api/datasources/{TYPE}/info`

---

### 🔹 第 2 步：定义接口

- 顶部显示当前数据源信息  
- SQL 使用 `#{param}` 参数自动发现输入参数  
- 支持两种操作类型：
  - `QUERY`（查询主库）
  - `MUTATION`（写入测试库）

示例：

```sql
-- 查询接口
SELECT id, name, email FROM users WHERE id = #{userId};

-- 变更接口
INSERT INTO users (name, email) VALUES (#{name}, #{email});
```

---

### 🔹 第 3 步：发布与测试

- 一键发布生成 GraphQL SDL，立即生效  
- SQL 测试通过 `/api/resolver-config/test-sql`  
- GraphQL 测试可直接在 `/graphiql` 中执行

---

## 🧪 GraphQL 示例

**查询：**

```graphql
query {
  getUserById(userId: 1) {
    id
    name
    email
  }
}
```

**变更（执行于测试库）：**

```graphql
mutation {
  createUser(name: "Alice", email: "alice@example.com") {
    id
    name
    email
  }
}
```

---

## 🔄 主库与测试库同步

- `Mutation` 默认执行在测试库  
- 同步接口：

```http
POST /api/admin/sandbox/reset
```

可能返回：
- `200/201`：同步完成  
- `202 {"jobId": "..."}`：异步任务执行中

轮询任务状态：

```http
GET /api/admin/sandbox/reset/{jobId}/status
```

返回：

```json
{
  "done": true,
  "success": true,
  "message": "测试数据库同步完成"
}
```

---

## 🧩 REST API 一览

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/resolver-config` | 获取所有配置 |
| POST | `/api/resolver-config` | 创建新配置 |
| PUT | `/api/resolver-config/{id}` | 更新配置 |
| DELETE | `/api/resolver-config/{id}` | 删除配置 |
| POST | `/api/resolver-config/test-sql` | 测试 SQL 查询 |
| GET | `/api/datasources/{TYPE}/status` | 检查数据源状态 |
| GET | `/api/datasources/{TYPE}/info` | 获取数据源信息 |
| POST | `/api/admin/sandbox/reset` | 重置测试库 |
| GET | `/api/admin/sandbox/reset/{jobId}/status` | 轮询任务状态 |

---

## 🧠 SQL 参数绑定

```sql
SELECT * FROM products WHERE price > #{minPrice} AND category = #{category};
```

输入参数配置：

```json
{
  "minPrice": "Float",
  "category": "String"
}
```

---

## ⚙️ Resolver 配置字段说明

| 字段 | 说明 |
|------|------|
| resolverName | GraphQL 字段名（唯一） |
| operationType | 操作类型：`QUERY` / `MUTATION` |
| sqlQuery | SQL 语句（支持参数化） |
| inputParameters | JSON 格式输入参数定义 |
| outputFields | JSON 格式输出字段定义 |
| description | 描述信息 |
| enabled | 是否启用 |
| dataSource | 所属数据源（`MYSQL` / `DM8`） |

---

## 🩺 故障排除

1. **数据库连接失败**
   - 检查数据库服务是否运行
   - 对 DM8 使用 `127.0.0.1`
2. **徽章显示“未配置”**
   - 检查 `application.properties` 中的配置项是否完整
3. **参数绑定错误**
   - 确认 SQL 中的 `#{参数}` 与输入参数 JSON 中的键名一致
4. **变更执行异常**
   - Mutation 默认写入测试库，若希望写入主库，请修改后端逻辑

---

## 🧩 开发与扩展

- **新增数据源**：修改 `MultiDataSourceConfig` 与 `DataSourceStatusController.PROBES`  
- **自定义 SQL 校验规则**：编辑 `DynamicSqlExecutor.validateSql()`  
- **新增前端数据源卡片**：修改 `/static/index.html` 与 `/static/script.js`

---

## 📜 许可证

MIT License
