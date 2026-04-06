# GitHive 命名空间（Namespace）设计文档

## 1. 背景与目标

GitHive 的核心路由模型为 `/{namespace}/{repo}`，用户和组织共享同一 URL 空间。为保证路径全局唯一、归属关系清晰，需要引入独立的 namespace 层来统一管理。

本次实现的目标：

- 建立独立的 `namespace` 表，作为用户（未来也包括组织）的 URL 标识层
- 用户注册时自动创建对应的 namespace，保证注册流程完整性
- 用户名和组织名在 namespace 层面互斥，避免 URL 冲突
- URL 大小写不敏感（仿 GitHub），同时保留原始大小写用于展示
- 对外提供 namespace 查询与可用性检查 API

---

## 2. 核心设计决策

### 2.1 为什么需要独立的 namespace 表？

| 方案 | 优势 | 劣势 |
|------|------|------|
| **直接用 `app_user.username` 做路由** | 简单直接，无额外表 | 用户和组织的名称唯一性无法在数据库层保证；仓库归属需要多态 owner（user_id / org_id），查询复杂 |
| **独立 namespace 表** | 单表 UNIQUE 约束即可保证全局唯一；仓库通过 `owner_namespace_id` 直接关联，无多态问题；为组织扩展预留了位置 | 多一张表，注册时多一次写入 |

**最终选择**：独立 namespace 表。核心理由：

1. **唯一性保证**：用户名和组织名共享 URL 空间，单独维护 namespace 表可通过数据库 UNIQUE 约束从根本上杜绝冲突
2. **归属模型简化**：后续仓库表只需 `owner_namespace_id BIGINT` 一个字段指向 namespace，不需要 `owner_type + owner_id` 的多态设计
3. **路由解耦**：URL 路径解析只查 namespace 表，不关心 owner 是用户还是组织

### 2.2 大小写策略

仿 GitHub 的做法：

- `path`：统一转小写存储，用于路由精确匹配，数据库列设 UNIQUE 约束
- `display_path`：保留原始大小写，用于页面展示

例如用户名 `Akika` 注册时，namespace 记录为 `path = "akika"`, `display_path = "Akika"`。访问 `/Akika/repo` 和 `/akika/repo` 都指向同一个 namespace。

查询时所有传入的 path 参数统一 `toLowerCase(Locale.ROOT)` 后与 `path` 列精确匹配，无需 `LOWER()` 函数或 COLLATE 配置，索引利用率最高。

### 2.3 所有者绑定

`owner_type (VARCHAR)` + `owner_id (BIGINT)` 联合唯一索引，保证一个所有者只拥有一个 namespace：

- `owner_type = "USER"` 时，`owner_id` 指向 `app_user.id`
- `owner_type = "ORG"` 时，`owner_id` 指向未来的 `organization.id`

该设计让 namespace 表成为用户/组织的"名片"，后续仓库、路由、权限等模块均通过 `namespace.id` 建立关联。

---

## 3. 数据模型

### 3.1 namespace 表

```sql
CREATE TABLE namespace (
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
    path               VARCHAR(40) NOT NULL UNIQUE,
    display_path       VARCHAR(40) NOT NULL,
    owner_type         VARCHAR(20) NOT NULL,        -- 'USER' / 'ORG'
    owner_id           BIGINT NOT NULL,
    created_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE UNIQUE INDEX uk_namespace_owner ON namespace(owner_type, owner_id);
CREATE INDEX idx_namespace_owner_type ON namespace(owner_type);
```

### 3.2 索引说明

| 索引 | 类型 | 用途 |
|------|------|------|
| `path` (列级 UNIQUE) | 唯一索引 | URL 路由查找；保证全局路径唯一 |
| `uk_namespace_owner` | 联合唯一索引 | 保证一个所有者只有一个 namespace |
| `idx_namespace_owner_type` | 普通索引 | 按类型过滤查询（如列出所有用户 / 所有组织） |

### 3.3 ER 关系

```
┌──────────────┐         ┌──────────────────┐
│   app_user   │         │    namespace     │
├──────────────┤    1:1  ├──────────────────┤
│ id (PK)      │◄────────│ owner_id         │
│ username     │         │ owner_type = USER│
│ ...          │         │ path (UNIQUE)    │
└──────────────┘         │ display_path     │
                         │ id (PK)          │
                         └────────┬─────────┘
                                  │ 1:N (未来)
                                  ▼
                         ┌──────────────────┐
                         │   repository     │
                         ├──────────────────┤
                         │ owner_ns_id (FK) │
                         │ slug             │
                         │ ...              │
                         └──────────────────┘
```

---

## 4. 模块详细设计

### 4.1 包结构

```
me.akika.githive
├── common/state/
│   └── NamespaceType.java              ← 枚举：USER / ORG
└── namespace/
    ├── entity/
    │   └── Namespace.java              ← MyBatis-Plus 实体
    ├── mapper/
    │   └── NamespaceMapper.java        ← BaseMapper
    ├── service/
    │   ├── NamespaceService.java       ← 服务接口
    │   └── impl/
    │       └── NamespaceServiceImpl.java
    ├── dto/
    │   ├── NamespaceResponse.java      ← 查询响应 DTO
    │   └── NamespaceAvailabilityResponse.java  ← 可用性检查响应 DTO
    └── controller/
        └── NamespaceController.java    ← REST API（@Public）
```

### 4.2 NamespaceType 枚举

**文件**：`common/state/NamespaceType.java`

```java
public enum NamespaceType {
    USER(0, "用户"),
    ORG(1, "组织");
}
```

遵循项目已有枚举模式（`code` + `description`），数据库中以枚举名称字符串存储（`VARCHAR(20)`）。

### 4.3 Namespace 实体

**文件**：`namespace/entity/Namespace.java`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `Long` | 主键，自增 |
| `path` | `String` | URL 路由路径，统一小写，全局唯一 |
| `displayPath` | `String` | 展示用路径，保留原始大小写 |
| `ownerType` | `NamespaceType` | 所有者类型 |
| `ownerId` | `Long` | 所有者 ID |
| `createdAt` | `LocalDateTime` | 创建时间 |
| `updatedAt` | `LocalDateTime` | 更新时间 |

遵循项目实体规范：`@TableName` + `@TableId(IdType.AUTO)` + Lombok 五件套。

### 4.4 NamespaceService

**文件**：`namespace/service/NamespaceService.java`

| 方法 | 签名 | 职责 |
|------|------|------|
| `createUserNamespace` | `(AppUser) → Namespace` | 用户注册时创建 namespace；`path = username.toLowerCase()`，`displayPath = username` |
| `findByPath` | `(String) → Namespace` | 根据 path 查询（输入自动转小写），不存在返回 `null` |
| `getById` | `(Long) → Namespace` | 根据 ID 查询 |
| `existsByPath` | `(String) → boolean` | 检查 path 是否已被占用（输入自动转小写） |
| `toResponse` | `(Namespace) → NamespaceResponse` | 实体转 DTO |

**设计要点**：

- `createUserNamespace` 标记 `@Transactional`，创建前二次检查 path 唯一性（防御并发场景下 `UNIQUE` 约束兜底）
- 所有接受 path 参数的方法内部统一 `toLowerCase(Locale.ROOT)`，调用方无需关心大小写

### 4.5 NamespaceController

**文件**：`namespace/controller/NamespaceController.java`

类级标记 `@Public`，以下接口无需 JWT 认证：

| HTTP 方法 | 路径 | 说明 | 响应体 |
|-----------|------|------|--------|
| `GET` | `/api/namespaces/{path}` | 查询 namespace 信息 | `ApiResponse<NamespaceResponse>` |
| `GET` | `/api/namespaces/{path}/availability` | 检查 path 可用性 | `ApiResponse<NamespaceAvailabilityResponse>` |

**接口行为**：

- 查询接口：path 不存在时抛出 `BusinessException`（400）
- 可用性检查：始终返回 200，通过 `available` 字段表示可用/已占用

### 4.6 响应 DTO

#### NamespaceResponse

```java
public class NamespaceResponse {
    private Long id;
    private String path;
    private String displayPath;
    private NamespaceType ownerType;
    private Long ownerId;
    private LocalDateTime createdAt;
}
```

#### NamespaceAvailabilityResponse

```java
public class NamespaceAvailabilityResponse {
    private String path;
    private boolean available;
    private String message;
}
```

---

## 5. 注册流程集成

### 5.1 变更点

`AuthServiceImpl` 的修改：

1. **新增依赖注入**：`NamespaceService`
2. **注册校验增强**：`validateRegisterRequest()` 中在用户名唯一性检查后，额外调用 `namespaceService.existsByPath(username)` 检查 namespace 是否已被占用（覆盖组织名抢占场景）
3. **初始化 namespace**：`initializeUserNamespace()` 从空方法改为调用 `namespaceService.createUserNamespace(user)`

### 5.2 注册流程（完整）

```
用户发起注册请求
│
├── 1. 校验用户名格式（不含空白字符）
├── 2. 校验用户名唯一性（app_user 表）
├── 3. 校验 namespace 路径唯一性（namespace 表）   ← 新增
├── 4. 校验邮箱唯一性（app_user 表）
├── 5. 验证码校验
│
├── 6. 插入 app_user 记录
├── 7. 创建 namespace 记录                         ← 新增
│       path = username.toLowerCase()
│       display_path = username
│       owner_type = USER
│       owner_id = user.id
│
├── 8. 消费验证码
└── 9. 返回 AuthUserResponse
```

**事务保证**：步骤 6-7 在同一个 `@Transactional` 事务中，用户记录和 namespace 记录要么同时成功，要么同时回滚。

### 5.3 MapperScan 扩展

`GitHiveApplication` 的 `@MapperScan` 从单包扩展为多包：

```java
@MapperScan({"me.akika.githive.auth.mapper", "me.akika.githive.namespace.mapper"})
```

---

## 6. API 使用示例

### 6.1 查询 namespace 信息

```
GET /api/namespaces/akika

200 OK
{
  "success": true,
  "message": "OK",
  "data": {
    "id": 1,
    "path": "akika",
    "displayPath": "Akika",
    "ownerType": "USER",
    "ownerId": 42,
    "createdAt": "2026-04-06T17:00:00"
  }
}
```

大小写不敏感 —— `GET /api/namespaces/Akika` 返回相同结果。

### 6.2 检查可用性

```
GET /api/namespaces/new-org/availability

200 OK
{
  "success": true,
  "message": "OK",
  "data": {
    "path": "new-org",
    "available": true,
    "message": "命名空间可用"
  }
}
```

---

## 7. 文件清单

### 新增文件

| 文件路径 | 说明 |
|---------|------|
| `common/state/NamespaceType.java` | 命名空间类型枚举（USER / ORG） |
| `namespace/entity/Namespace.java` | 命名空间实体 |
| `namespace/mapper/NamespaceMapper.java` | MyBatis-Plus Mapper |
| `namespace/service/NamespaceService.java` | 服务接口 |
| `namespace/service/impl/NamespaceServiceImpl.java` | 服务实现 |
| `namespace/dto/NamespaceResponse.java` | 查询响应 DTO |
| `namespace/dto/NamespaceAvailabilityResponse.java` | 可用性检查响应 DTO |
| `namespace/controller/NamespaceController.java` | REST Controller |
| `src/main/resources/sql/namespace-schema.sql` | MySQL 生产 Schema |
| `src/test/resources/sql/auth-schema-h2.sql` | H2 测试 Schema（含 auth + namespace） |

### 修改文件

| 文件路径 | 变更内容 |
|---------|---------|
| `GitHiveApplication.java` | `@MapperScan` 增加 `namespace.mapper` 包 |
| `auth/service/impl/AuthServiceImpl.java` | 注入 `NamespaceService`；注册校验增加 namespace 唯一性检查；`initializeUserNamespace()` 调用 `NamespaceService` |

---

## 8. 后续演进

### 8.1 组织 namespace

当实现 `org` 模块时，组织创建流程调用 `NamespaceService` 创建组织 namespace：

```java
// 未来 OrgServiceImpl 中
Namespace ns = Namespace.builder()
        .path(orgName.toLowerCase())
        .displayPath(orgName)
        .ownerType(NamespaceType.ORG)
        .ownerId(org.getId())
        .build();
```

由于用户和组织共享 `namespace.path` 的 UNIQUE 约束，数据库层面天然互斥，无需额外协调逻辑。

### 8.2 仓库模块集成

仓库表通过 `owner_namespace_id` 直接关联 namespace，路由解析流程：

```
请求 /{owner}/{repo}
│
├── 1. NamespaceService.findByPath(owner)  → namespace
├── 2. RepoService.findByNamespaceAndSlug(namespace.id, repo)  → repository
└── 3. 权限检查 + 返回数据
```

无需关心 owner 是用户还是组织，路由层只和 namespace 打交道。

### 8.3 仓库转移

仓库转移时，更新 `repository.owner_namespace_id` 指向新的 namespace，仓库自身的 `id` 保持不变。URL 从 `/{old-ns}/{repo}` 变为 `/{new-ns}/{repo}`，可配合重定向表实现旧路径的 301 跳转。

### 8.4 可扩展方向

| 方向 | 说明 |
|------|------|
| **namespace 改名** | 更新 `path` + `display_path`，配合重定向表保证旧 URL 仍可访问 |
| **保留名称** | 维护保留名称列表（如 `admin`、`api`、`settings`），在 `existsByPath` 中额外检查 |
| **namespace 主页** | `GET /{namespace}` 展示用户/组织的 profile 和仓库列表 |
