# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

GitHive is a GitHub-like Git hosting platform backend built with Java 21 and Spring Boot 3.5. Currently in early development — authentication and namespace modules are implemented. The project uses Chinese-language user-facing messages and documentation.

## Build & Run Commands

```bash
./mvnw clean compile              # Compile
./mvnw spring-boot:run            # Run the application
./mvnw package                    # Build JAR (runs tests)
./mvnw package -DskipTests        # Build JAR (skip tests)
```

## Testing

Tests use JUnit 5 with H2 in-memory database (MySQL compatibility mode). Test config is in `src/test/resources/application.yaml`.

```bash
./mvnw test                                               # Run all tests
./mvnw test -Dtest=GitHiveApplicationTests                # Run a specific test class
./mvnw test -Dtest=GitHiveApplicationTests#contextLoads   # Run a single test method
```

**Note:** Test config references `classpath:sql/auth-schema-h2.sql` for schema initialization — this file must exist under `src/test/resources/sql/` for Spring context to load.

## Architecture

### Tech Stack
- **Framework:** Spring Boot 3.5 (spring-boot-starter-web, starter-validation)
- **ORM:** MyBatis-Plus 3.5.7 with MySQL
- **Auth:** Custom lightweight JWT via Spring MVC Interceptor (NOT Spring Security) — uses JJWT 0.12.6
- **Password hashing:** BCrypt via `spring-security-crypto` (only this module from Spring Security)
- **API docs:** Knife4j 4.5 + SpringDoc OpenAPI 2.8 — Swagger UI at `/doc.html`
- **Utilities:** Lombok, commons-lang3

### Package Structure (`me.akika.githive`)

- **`auth/`** — Authentication module
  - `controller/AuthController` — REST endpoints at `/api/auth/**` (register, login, refresh, logout, captcha)
  - `interceptor/JwtAuthInterceptor` — Core JWT validation interceptor registered for all `/api/**` paths
  - `jwt/JwtTokenProvider` — JWT sign/parse/validate utility
  - `context/AuthContext` — ThreadLocal holder for current user; `LoginUser` is the lightweight DTO
  - `annotation/` — `@Public` (skip JWT requirement), `@RequireRole` (role-based access), `@CurrentUser` (parameter injection)
  - `service/` — `AuthService` interface + `AuthServiceImpl`; `MockCaptchaService` for dev/test
  - `mapper/` — MyBatis-Plus mappers (`AppUserMapper`, `UserRefreshTokenMapper`)
  - `entity/` — `AppUser`, `UserRefreshToken`
  - `dto/` — Request/response DTOs

- **`namespace/`** — Namespace module (users and orgs share one URL space)
  - `controller/NamespaceController` — REST endpoints at `/api/namespaces/**` (`@Public`): query by path, check availability
  - `service/` — `NamespaceService` interface + `NamespaceServiceImpl`: create user namespace, find/check by path
  - `mapper/NamespaceMapper` — MyBatis-Plus mapper
  - `entity/Namespace` — Fields: `path` (lowercase, UNIQUE, for routing), `displayPath` (original case, for display), `ownerType`, `ownerId`
  - `dto/` — `NamespaceResponse`, `NamespaceAvailabilityResponse`
  - User registration automatically creates a namespace; path = `username.toLowerCase()`, displayPath = `username`
  - Registration validates namespace uniqueness so user and org names never collide in URL space

- **`common/`** — Shared infrastructure
  - `api/ApiResponse` — Unified response wrapper `{success, message, data}`
  - `config/WebMvcConfig` — Registers `JwtAuthInterceptor` for `/api/**` and excludes Swagger paths
  - `config/PasswordEncoderConfig` — BCrypt bean
  - `config/OpenApiConfig` — Swagger/OpenAPI setup
  - `exception/GlobalExceptionHandler` — `@RestControllerAdvice` mapping exceptions to 400/401/403
  - `state/` — Enums: `SystemRole` (SYSTEM_USER, SYSTEM_ADMIN), `UserState` (ACTIVE, INACTIVE, BANNED), `NamespaceType` (USER, ORG)

### Request Flow

All `/api/**` requests pass through `JwtAuthInterceptor` (registered in `WebMvcConfig`):
1. Check for `@Public` annotation on method/class
2. Extract and validate JWT from `Authorization: Bearer <token>` header
3. Populate `AuthContext` (ThreadLocal) with `LoginUser`
4. Check `@RequireRole` if present — 401 if unauthenticated, 403 if role mismatch
5. `afterCompletion` always calls `AuthContext.clear()` to prevent ThreadLocal leaks

### Key Conventions
- New endpoints default to **JWT-protected**; use `@Public` to opt out
- Controller layer: inject current user via `@CurrentUser LoginUser user` parameter
- Service layer: use `AuthContext.requiredCurrentUser()` to get current user
- All API responses wrapped in `ApiResponse<T>`
- Custom exceptions (`BusinessException`, `AuthenticationException`, `AuthorizationException`) handled globally
- Namespace path always stored lowercase; all `findByPath`/`existsByPath` calls normalize input via `toLowerCase(Locale.ROOT)`
- Future repository table should reference `namespace.id` via `owner_namespace_id` (not polymorphic user/org ID)

## Configuration

App config is in `src/main/resources/application.yaml`. Key properties configurable via environment variables:
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` — MySQL connection
- `JWT_SECRET` — JWT signing key
- `app.security.jwt.*` — token expiry settings (in `AuthProperties.java` via `@ConfigurationProperties`)

## SQL Schema

Production schemas are in `src/main/resources/sql/` (per-module, MySQL syntax):
- `auth-schema.sql` — `app_user`, `user_refresh_token`
- `namespace-schema.sql` — `namespace`

Test schema is in `src/test/resources/sql/auth-schema-h2.sql` (H2 syntax, all tables combined). When adding new tables, update both the production SQL and this test file.

## Design Docs

Detailed design documents are in `docs/` (Chinese):
- `auth-design.md` — JWT auth interceptor, annotations, auth context
- `namespace-design.md` — Namespace table design, case strategy, registration integration, future repo routing

## Entry Point

`GitHiveApplication.java` — annotated with `@MapperScan({"me.akika.githive.auth.mapper", "me.akika.githive.namespace.mapper"})` for MyBatis-Plus mapper scanning. New modules must add their mapper package here.
