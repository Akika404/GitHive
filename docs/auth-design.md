# GitHive JWT 鉴权体系设计文档

## 1. 背景与目标

GitHive 在认证模块中已实现用户注册、登录、Token 签发与刷新等基础流程，但所有 API 接口均处于"裸奔"状态 —— 缺少 JWT 校验、角色鉴权和统一的认证上下文传递机制。

本次改造的目标：

- 为所有 `/api/**` 接口默认开启 JWT 认证（安全优先原则）
- 提供声明式注解，让开发者以最小成本控制接口的访问级别
- 建立请求级别的用户上下文，使 Controller / Service 层可便捷获取当前登录用户
- 统一认证与授权异常的 HTTP 响应格式（401 / 403）

---

## 2. 技术选型

### 2.1 备选方案对比

| 方案 | 优势 | 劣势 |
|------|------|------|
| **Spring Security（完整）** | 生态成熟，功能覆盖全面（OAuth2、CSRF、Session、方法级安全） | 过于重量级；Filter Chain 配置复杂；后期迁移到网关统一鉴权时需要大量拆除工作 |
| **Sa-Token** | API 简洁，开箱即用 | 引入额外依赖；与 Spring 生态耦合度低；部分功能与网关鉴权重叠 |
| **自定义轻量方案（Spring MVC Interceptor + 注解）** | 零额外依赖；代码量可控；与现有架构无缝集成；迁移到网关时只需删除 Interceptor | 需要自行维护，功能边界有限 |

### 2.2 最终选择

**自定义轻量方案**，基于以下判断：

1. **未来架构**：项目规划了 API 网关层进行统一鉴权，当前服务内鉴权只是过渡阶段，越轻量越好拆
2. **实际需求**：当前仅需 JWT 校验 + 角色控制，不涉及 OAuth2、CSRF、Session 管理等
3. **已有依赖**：项目已引入 `spring-security-crypto`（仅用 BCrypt），无需升级为 `spring-boot-starter-security`
4. **可维护性**：全部代码在项目内，逻辑透明，团队成员可以快速理解和修改

---

## 3. 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        HTTP Request                             │
│                  Authorization: Bearer <JWT>                    │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────────────┐
│                    WebMvcConfig                                  │
│              (注册 Interceptor + ArgumentResolver)                │
└──────────────────────────┬───────────────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────────────┐
│                  JwtAuthInterceptor                              │
│                                                                  │
│  1. 判断 @Public → 公开接口可选认证，保护接口强制认证                  │
│  2. 提取 Authorization Header → Bearer Token                     │
│  3. JwtTokenProvider.parseToken() → 校验签名 / 过期 / issuer       │
│  4. 构建 LoginUser → AuthContext.setCurrentUser()                 │
│  5. 检查 @RequireRole → 角色不匹配则 403                           │
│  6. afterCompletion() → AuthContext.clear()                      │
└──────────────────────────┬───────────────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────────────┐
│                      Controller                                  │
│                                                                  │
│  @CurrentUser LoginUser user  ←  CurrentUserArgumentResolver     │
│  AuthContext.requiredCurrentUser()  ←  Service 层直接获取           │
└──────────────────────────────────────────────────────────────────┘
                           │
                    异常时由全局处理
                           │
                           ▼
┌──────────────────────────────────────────────────────────────────┐
│                 GlobalExceptionHandler                            │
│                                                                  │
│  AuthenticationException  →  HTTP 401  {"success":false, ...}    │
│  AuthorizationException   →  HTTP 403  {"success":false, ...}    │
└──────────────────────────────────────────────────────────────────┘
```

---

## 4. 模块详细设计

### 4.1 JwtTokenProvider — JWT 令牌工具

**文件**: `auth/util/JwtTokenProvider.java`

原有实现仅包含 `generateAccessToken()`，本次补全了解析与验证侧的能力：

| 方法 | 职责 |
|------|------|
| `generateAccessToken(AppUser, LocalDateTime)` | 签发 JWT（已有，调整 role claim 序列化为枚举名称字符串） |
| `parseToken(String) → Optional<Claims>` | 解析并验证 JWT，校验签名、过期时间、issuer；无效则返回 empty |
| `validateToken(String) → boolean` | 快速校验 Token 有效性 |
| `getUserId(String) → Optional<Long>` | 提取 subject（用户 ID） |
| `getUsername(String) → Optional<String>` | 提取 username claim |
| `getRole(String) → Optional<String>` | 提取 role claim |

**设计要点**：

- `parseToken` 捕获 `ExpiredJwtException`、`JwtException`、`IllegalArgumentException` 三类异常，以 `debug` 级别记录日志，避免无效 Token 产生大量告警
- 使用 `requireIssuer()` 确保 Token 来源可信
- role claim 改为存储 `SystemRole.name()`（如 `"SYSTEM_ADMIN"`），解析时通过 `SystemRole.valueOf()` 还原枚举，避免序列化歧义

### 4.2 认证上下文

#### LoginUser — 轻量用户 DTO

**文件**: `auth/context/LoginUser.java`

```java
public class LoginUser {
    private final Long userId;
    private final String username;
    private final SystemRole role;
}
```

仅承载从 JWT 中提取的必要信息，不包含敏感字段（密码、邮箱等）。全字段 final + Builder 模式，线程安全。

#### AuthContext — ThreadLocal 持有者

**文件**: `auth/context/AuthContext.java`

| 方法 | 说明 |
|------|------|
| `setCurrentUser(LoginUser)` | 由 Interceptor 在 preHandle 中调用 |
| `getCurrentUser() → Optional<LoginUser>` | 安全获取，可能为空 |
| `requiredCurrentUser() → LoginUser` | 断言获取，为空则抛 IllegalStateException |
| `clear()` | 由 Interceptor 在 afterCompletion 中调用，防止 ThreadLocal 泄漏 |

**使用场景**：

- Controller 层：优先使用 `@CurrentUser` 注解注入
- Service 层：调用 `AuthContext.requiredCurrentUser()`（仅限确定在认证保护下的调用链）

### 4.3 自定义注解

#### @Public — 公开接口标记

**文件**: `auth/annotation/Public.java`

- 支持 **方法级** 和 **类级**
- 标记后该接口不强制 JWT 认证
- 如果请求携带了有效 Token，仍然会解析并填充 AuthContext（支持"可选认证"场景）
- 典型用途：登录、注册、验证码、健康检查等

#### @RequireRole — 角色鉴权

**文件**: `auth/annotation/RequireRole.java`

```java
@RequireRole(SystemRole.SYSTEM_ADMIN)
@RequireRole({SystemRole.SYSTEM_ADMIN, SystemRole.SYSTEM_USER})  // OR 逻辑
```

- 支持 **方法级** 和 **类级**（方法级优先）
- 隐含认证要求：未认证时返回 401，已认证但角色不匹配时返回 403
- 多角色为 OR 关系（满足任一即可）

#### @CurrentUser — 参数注入

**文件**: `auth/annotation/CurrentUser.java`

```java
@GetMapping("/me")
public ApiResponse<UserVO> me(@CurrentUser LoginUser user) { ... }
```

- 仅支持 **参数级**
- 参数类型必须为 `LoginUser`
- 未认证时注入 `null`（在 `@Public` 接口上使用时需注意空值处理）

### 4.4 JwtAuthInterceptor — 核心拦截器

**文件**: `auth/interceptor/JwtAuthInterceptor.java`

#### 处理流程

```
preHandle(request, response, handler)
│
├── handler 不是 HandlerMethod？ → 直接放行（静态资源等）
│
├── 检查 @Public 注解（方法级 → 类级）
│
├── 提取 Authorization Header
│   └── 以 "Bearer " 开头 → 截取 Token
│
├── Token 存在？
│   ├── parseToken() 成功 → 构建 LoginUser → AuthContext.setCurrentUser()
│   └── parseToken() 失败
│       ├── @Public 接口 → 静默跳过（可选认证）
│       └── 保护接口 → throw AuthenticationException("身份认证失败，令牌无效或已过期")
│
├── Token 不存在？
│   ├── @Public 接口 → 放行
│   └── 保护接口 → throw AuthenticationException("未提供身份认证令牌")
│
└── 检查 @RequireRole
    ├── 无注解 → 放行
    ├── 未认证 → throw AuthenticationException
    └── 角色不匹配 → throw AuthorizationException("权限不足，无法访问该资源")
```

#### afterCompletion

无条件调用 `AuthContext.clear()`，确保 ThreadLocal 不会因线程池复用而泄漏到后续请求。

### 4.5 CurrentUserArgumentResolver

**文件**: `auth/resolver/CurrentUserArgumentResolver.java`

- 实现 `HandlerMethodArgumentResolver` 接口
- 匹配条件：参数有 `@CurrentUser` 注解 **且** 类型为 `LoginUser`
- 从 `AuthContext.getCurrentUser()` 获取值，未认证时返回 `null`

### 4.6 WebMvcConfig — 组件注册

**文件**: `common/config/WebMvcConfig.java`

#### 拦截器注册

```java
registry.addInterceptor(jwtAuthInterceptor)
    .addPathPatterns("/api/**")           // 仅拦截业务 API
    .excludePathPatterns(                 // 排除框架路径
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/v3/api-docs/**",
        "/doc.html",
        "/webjars/**",
        "/error"
    );
```

**设计要点**：

- 拦截范围限定为 `/api/**`，Swagger / Knife4j / Spring Boot Error 页面不受影响
- 接口级的公开/保护控制通过 `@Public` 注解完成，不在路径配置中维护白名单（避免白名单与注解不同步）

### 4.7 异常处理

**文件**: `common/exception/GlobalExceptionHandler.java`（修改）

新增两个异常处理器：

| 异常类 | HTTP 状态码 | 响应体 |
|--------|------------|--------|
| `AuthenticationException` | 401 Unauthorized | `{"success": false, "message": "...", "data": null}` |
| `AuthorizationException` | 403 Forbidden | `{"success": false, "message": "...", "data": null}` |

响应格式与项目已有的 `ApiResponse` 保持一致。

---

## 5. 使用指南

### 5.1 默认行为

所有 `/api/**` 下的 Controller 接口**默认需要 JWT 认证**。请求必须携带：

```
Authorization: Bearer <access_token>
```

### 5.2 公开接口

在 Controller 类或方法上添加 `@Public`：

```java
// 整个 Controller 公开
@Public
@RestController
@RequestMapping("/api/auth")
public class AuthController { ... }

// 单个方法公开
@Public
@GetMapping("/api/health")
public ApiResponse<String> health() {
    return ApiResponse.success("OK");
}
```

### 5.3 角色控制

```java
// 仅系统管理员可访问
@RequireRole(SystemRole.SYSTEM_ADMIN)
@DeleteMapping("/api/admin/users/{id}")
public ApiResponse<Void> deleteUser(@PathVariable Long id) { ... }

// 管理员或普通用户均可访问（OR 逻辑）
@RequireRole({SystemRole.SYSTEM_ADMIN, SystemRole.SYSTEM_USER})
@GetMapping("/api/projects")
public ApiResponse<List<ProjectVO>> listProjects() { ... }
```

### 5.4 获取当前用户

**Controller 层** — 参数注入：

```java
@GetMapping("/api/user/me")
public ApiResponse<UserVO> me(@CurrentUser LoginUser user) {
    return ApiResponse.success(userService.getProfile(user.getUserId()));
}
```

**Service 层** — 静态方法：

```java
public void createRepository(CreateRepoRequest request) {
    LoginUser currentUser = AuthContext.requiredCurrentUser();
    // currentUser.getUserId()
    // currentUser.getUsername()
    // currentUser.getRole()
}
```

### 5.5 可选认证（公开接口识别登录用户）

某些公开接口在用户已登录时需要展示个性化内容：

```java
@Public
@GetMapping("/api/explore")
public ApiResponse<ExploreVO> explore(@CurrentUser LoginUser user) {
    if (user != null) {
        // 已登录 — 展示个性化推荐
    } else {
        // 未登录 — 展示默认内容
    }
}
```

`@Public` 接口在请求携带有效 Token 时仍会解析并填充 AuthContext，但不会因无 Token 或无效 Token 而拒绝请求。

---

## 6. 文件清单

### 新增文件

| 文件路径 | 说明 |
|---------|------|
| `auth/annotation/Public.java` | 公开接口注解 |
| `auth/annotation/RequireRole.java` | 角色鉴权注解 |
| `auth/annotation/CurrentUser.java` | 当前用户参数注入注解 |
| `auth/context/LoginUser.java` | 登录用户 DTO |
| `auth/context/AuthContext.java` | ThreadLocal 认证上下文 |
| `auth/interceptor/JwtAuthInterceptor.java` | JWT 认证拦截器 |
| `auth/resolver/CurrentUserArgumentResolver.java` | @CurrentUser 参数解析器 |
| `common/config/WebMvcConfig.java` | MVC 配置（注册拦截器与解析器） |
| `common/exception/AuthenticationException.java` | 认证异常（401） |
| `common/exception/AuthorizationException.java` | 授权异常（403） |

### 修改文件

| 文件路径 | 变更内容 |
|---------|---------|
| `auth/util/JwtTokenProvider.java` | 补全 parseToken / validateToken / getUserId / getUsername / getRole；修复 role claim 序列化 |
| `auth/controller/AuthController.java` | 添加 `@Public` 类级注解 |
| `common/exception/GlobalExceptionHandler.java` | 新增 AuthenticationException(401) 和 AuthorizationException(403) 处理器 |

---

## 7. 后续演进

### 7.1 网关迁移路径

当引入 API 网关（如 Spring Cloud Gateway）进行统一鉴权时：

1. 网关层负责 JWT 校验，将用户信息通过 Header（如 `X-User-Id`、`X-User-Role`）透传给下游服务
2. 将 `JwtAuthInterceptor` 替换为 `GatewayUserInterceptor`，从 Header 中提取用户信息填充 AuthContext
3. `@Public`、`@RequireRole`、`@CurrentUser`、`AuthContext` 等组件无需修改，继续在服务内使用
4. 删除 `JwtTokenProvider` 的解析方法（仅保留签发，或整体迁移到网关/认证服务）

### 7.2 可扩展方向

| 方向 | 说明 |
|------|------|
| **权限细化** | 新增 `@RequirePermission` 注解，支持资源级权限控制（如仓库读写权限） |
| **Token 黑名单** | 配合 Redis 实现 access token 主动失效（修改密码、管理员封禁等场景） |
| **审计日志** | 在 Interceptor 的 afterCompletion 中记录操作审计日志 |
| **限流** | 基于用户维度的接口限流（可复用 AuthContext 中的用户信息） |
