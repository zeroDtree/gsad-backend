# GSAD 启动流程图

本文描述从 `docker compose up` 到 Backend 就绪的完整启动链路，涵盖镜像构建、容器编排、JVM/Spring Boot 初始化与可选 Mock 组件。

---

## 总览：Docker Compose 启动顺序

默认命令：

```bash
docker compose up --build
```

可选 GPU 指标 Mock（profile）：

```bash
docker compose --profile gpu-server-report-mock up --build
```

```mermaid
flowchart TB
    subgraph user [用户]
        CMD["docker compose up --build"]
    end

    subgraph build [镜像构建 并行]
        B1["build backend<br/>Dockerfile multi-stage"]
        B2["build account-provision-mock<br/>dev/account-provision-mock"]
        B3["build gpu-server-report-mock<br/>profile 时"]
    end

    subgraph infra [基础设施 并行启动]
        PG["postgres:16<br/>:5432"]
        RD["redis:7<br/>:6379"]
    end

    subgraph health [健康检查通过后]
        BE["backend<br/>:8080"]
        AP["account-provision-mock<br/>轮询 pending 任务"]
    end

    subgraph optional [可选 profile]
        GPU["gpu-server-report-mock<br/>每 30s 上报"]
    end

    CMD --> build
    build --> infra
    PG -->|"pg_isready healthy"| health
    RD -->|"redis-cli ping healthy"| health
    health --> BE
    BE -->|"service_healthy"| AP
    AP -->|"POST report / provision/complete"| BE
    BE -->|"service_started"| optional
    GPU -->|"POST /api/internal/servers/report"| BE
    BE --> PG
    BE --> RD
```

| 阶段 | 服务 | 端口 | 就绪条件 |
|------|------|------|----------|
| 1 | postgres | 5432 | `pg_isready` |
| 1 | redis | 6379 | `PING` 成功 |
| 2 | backend | 8080 | postgres + redis healthy 后启动 |
| 3 | account-provision-mock | — | backend healthy 后开始轮询 report |
| 4（可选） | gpu-server-report-mock | — | backend `started` 后开始循环上报 |

---

## Backend 镜像构建流程（Dockerfile）

```mermaid
flowchart LR
    subgraph stage1 [Stage 1 build]
        S1A["FROM maven:3.9-eclipse-temurin-21-alpine"]
        S1B["COPY pom.xml"]
        S1C["mvn dependency:go-offline<br/>预下载依赖到 ~/.m2"]
        S1D["COPY src/"]
        S1E["mvn package -DskipTests<br/>产出 gsad-*.war"]
        S1A --> S1B --> S1C --> S1D --> S1E
    end

    subgraph stage2 [Stage 2 runtime]
        S2A["FROM eclipse-temurin:21-jre-alpine"]
        S2B["创建非 root 用户 gsad"]
        S2C["COPY war → /app/app.war"]
        S2D["ENTRYPOINT java -jar app.war"]
        S2A --> S2B --> S2C --> S2D
    end

    stage1 -->|"COPY --from=build"| stage2
```

**缓存策略**：仅 `pom.xml` 变更时复用 `go-offline` 层；仅 `src/` 变更时复用依赖层、只重新 `package`。

---

## JVM 启动入口约定

```mermaid
flowchart TD
    EP["Docker ENTRYPOINT<br/>java -jar app.war"]
    MF["读取 META-INF/MANIFEST.MF"]
    MC["Main-Class<br/>WarLauncher"]
    SC["Start-Class<br/>com.zerodtree.gsad.GsadApplication"]
    MAIN["GsadApplication.main()"]
    RUN["SpringApplication.run(...)"]

    EP --> MF --> MC --> SC --> MAIN --> RUN
```

| 层级 | 说明 |
|------|------|
| Dockerfile | 只指定 `java -jar`，不写 Java 类名 |
| `spring-boot-maven-plugin` | 打包时写入 `Start-Class` |
| `GsadApplication` | `@SpringBootApplication` + `main` 方法 |
| `ServletInitializer` | 仅外部 Tomcat 部署 WAR 时使用；`java -jar` 不走此路径 |

---

## Spring Boot 应用启动流程（backend 容器内）

```mermaid
flowchart TB
    START["GsadApplication.main()"]

    subgraph boot [Spring Boot 引导]
        A1["加载 application.properties<br/>+ 环境变量覆盖"]
        A2["@SpringBootApplication 组件扫描<br/>com.zerodtree.gsad.*"]
        A3["自动配置 AutoConfiguration"]
    end

    subgraph data [数据层初始化]
        D1["HikariCP 连接池<br/>jdbc:postgresql://postgres:5432/gsad"]
        D2["Flyway migrate<br/>classpath:db/migration V1..V8"]
        D3["Hibernate JPA<br/>ddl-auto=validate"]
        D4["Redis 连接<br/>spring.data.redis.*"]
    end

    subgraph web [Web 与安全]
        W1["内嵌 Tomcat 绑定 :8080"]
        W2["SecurityFilterChain<br/>JWT 过滤器"]
        W3["Controller / RestController 映射"]
        W4["SpringDoc Swagger 注册"]
    end

    subgraph bg [后台任务]
        G1["@EnableScheduling<br/>ExpirationScheduler 60s"]
    end

    READY["Started GsadApplication<br/>API 可访问"]

    START --> boot
    A1 --> D1
    D1 --> D2
    D2 --> D3
    D3 --> D4
    D4 --> web
    W1 --> W2 --> W3 --> W4
    W4 --> bg
    G1 --> READY
```

### 1 关键配置与环境变量（compose → backend）

```mermaid
flowchart LR
    ENV[".env / compose environment"]
    ENV --> DB["DB_HOST / DB_USER / DB_PASSWORD"]
    ENV --> RD["REDIS_HOST / REDIS_PASSWORD"]
    ENV --> JWT["JWT_SECRET"]
    ENV --> PSK["AGENT_PSK"]

    DB --> DS["spring.datasource.*"]
    RD --> RED["spring.data.redis.*"]
    JWT --> J["jwt.secret"]
    PSK --> A["agent.psk"]
```

### 2 Flyway 首次启动（数据库为空）

```mermaid
flowchart TD
    F0["Flyway 连接 postgres"]
    F1{"schema 是否存在?"}
    F2["baseline-on-migrate"]
    F3["执行 V1 init_schema"]
    F4["V2 indexes"]
    F5["V3 seed admin"]
    F6["V4 audit status"]
    F7["V5 metrics_json"]
    F8["V6 server binding + ssh"]
    F9["V7 004..030 mock 共 30 台"]
    F10["V8 drop peer_id"]
    F11["Hibernate validate 表结构"]
    OK["数据层就绪"]

    F0 --> F1
    F1 -->|否| F2 --> F3 --> F4 --> F5 --> F6 --> F7 --> F8 --> F9 --> F10 --> F11 --> OK
    F1 -->|是且版本落后| F3
    F1 -->|已是最新 V8| F11 --> OK
```

---

## 基础设施容器启动细节

### 1 postgres

```mermaid
flowchart LR
    P1["容器启动 postgres:16"]
    P2["POSTGRES_DB=gsad"]
    P3["挂载 postgres_data 卷"]
    P4["healthcheck pg_isready"]
    P5["healthy → backend 可连接"]
    P1 --> P2 --> P3 --> P4 --> P5
```

### 2 redis

```mermaid
flowchart LR
    R1["容器启动 redis:7-alpine"]
    R2["requirepass REDIS_PASSWORD"]
    R3["healthcheck redis-cli ping"]
    R4["healthy → 幂等 Key 可用"]
    R1 --> R2 --> R3 --> R4
```

### 3 account-provision-mock

```mermaid
flowchart LR
    N1["provision_loop.py<br/>每 PROVISION_POLL_INTERVAL 秒"]
    N2["POST report → 读取 pendingGrants / pendingRevokes"]
    N3["POST provision/complete 或 revoke/complete"]
    N4["backend healthy 后启动"]
    N1 --> N2 --> N3
    N4 --> N1
```

契约详见 [agent-provision.md](./agent-provision.md)。

---

## 可选：gpu-server-report-mock 启动（profile）

> 默认 dev 栈中 `account-provision-mock` 已自带 report 轮询；此 profile 仅用于单独压测指标上报。

```mermaid
flowchart TB
    P["profile gpu-server-report-mock"]
    P --> C["容器启动 report-loop.sh"]
    C --> E["读取 REPORT_API_URL / AGENT_PSK / MOCK_SERVER_COUNT"]
    E --> L["循环 1..30 台 mock 服务器"]
    L --> R["POST /api/internal/servers/report<br/>Header: X-Agent-PSK"]
    R --> S["ServerService.upsertReport<br/>更新 metrics / ONLINE"]
    S --> W["sleep AGENT_REPORT_INTERVAL 30s"]
    W --> L
```

---

## 启动完成后的运行时拓扑

```mermaid
flowchart TB
    User["浏览器 / 前端 dev proxy"]
    Provision["account-provision-mock"]
    Agent["gpu-server-report-mock 可选"]

    User -->|"8080 REST API"| Backend["backend GsadApplication"]
    Provision -->|"report + complete"| Backend
    Agent -->|"internal report"| Backend

    Backend --> Postgres[(postgres)]
    Backend --> Redis[(redis)]

    Backend --> Sched["ExpirationScheduler 每 60s"]
    Sched -->|"记录到期 ACTIVE 数量"| Backend
    Provision -->|"report 拉 pendingRevokes"| Backend
```

---

## 启动后快速验证

| 检查项 | 命令 / 地址 |
|--------|-------------|
| Backend 存活 | `curl -s http://localhost:8080/api/public/servers` |
| 账号开通 mock | 容器日志见 `account-provision-mock` 的 `provision complete` |
| Swagger | http://localhost:8080/swagger-ui.html |
| 服务器数量 | Flyway 种子后应有 30 台 mock GPU |
| GPU 动态上报 | 启用 profile 后日志见 `[gpu-server-report-mock] reported ...` |

---

## 常见启动失败点

```mermaid
flowchart TD
    E1["8080 端口被占用"]
    E2["孤儿容器未清理"]
    E3["postgres / redis 未 healthy"]
    E4["Flyway 迁移失败"]
    E5["JWT_SECRET / DB_PASSWORD 未配置"]

    E1 --> FIX1["docker compose down --remove-orphans"]
    E2 --> FIX1
    E3 --> FIX3["检查 .env 与容器日志"]
    E4 --> FIX4["检查 migration SQL / DB 状态"]
    E5 --> FIX5["配置 .env 后重启 compose"]
```

---

## 相关文件索引

| 文件 | 作用 |
|------|------|
| [docker-compose.yml](./docker-compose.yml) | 服务编排与依赖顺序 |
| [Dockerfile](./Dockerfile) | Backend 镜像构建与 `java -jar` 入口 |
| [pom.xml](./pom.xml) | Maven 依赖与 `spring-boot-maven-plugin` 打包 |
| [GsadApplication.java](./src/main/java/com/zerodtree/gsad/GsadApplication.java) | Spring Boot 主类 |
| [application.properties](./src/main/resources/application.properties) | 运行时配置 |
| [db/migration/](./src/main/resources/db/migration/) | Flyway 数据库迁移 |
| [agent-provision.md](./agent-provision.md) | 内部 report / complete 契约 |
| [dev/account-provision-mock/](./dev/account-provision-mock/) | Dev 环境模拟外部 provisioner |


# Spring 注解分层

```mermaid
flowchart TB
    P0(["阶段0 · IoC 容器<br/>Bean 注册表 · 实例化 · 依赖注入 · 生命周期"])

    subgraph P1["阶段1 · 启动引导"]
        Boot["@SpringBootApplication"]
        BootDecompose["= @Configuration<br/>+ @ComponentScan<br/>+ @EnableAutoConfiguration"]
        Enable["@Enable* 功能开关<br/>@EnableAsync · @EnableScheduling · @EnableWebSecurity"]
    end

    subgraph P2["阶段2 · 注册 Bean"]
        direction TB
        P2a["2a 扫描注册 · 类即 Bean<br/>@Component 系：@Service · @Repository · @RestController<br/>组件扫描发现 → Spring 直接实例化"]
        P2b["2b 工厂注册 · 方法产出 Bean<br/>@Configuration 类上的 @Bean 方法<br/>自建对象 · 第三方类 · 多 Bean 定制"]
        P2c["2c 自动配置 · Boot 写好的工厂<br/>starter 内 @Configuration + @Bean<br/>@ConditionalOnClass / OnMissingBean 按需生效<br/>例：DataSource · Redis · Security"]
        Props["配置绑定<br/>@ConfigurationProperties<br/>properties / env → 配置对象"]
    end

    P3["阶段3 · 依赖装配<br/>构造器注入（推荐）· @Autowired · @Value<br/>Bean 创建时解析依赖并完成注入"]

    subgraph P4["阶段4 · 运行期增强 AOP"]
        P4tx["@Transactional 事务"]
        P4async["@Async 异步 · 需 @EnableAsync"]
        P4sched["@Scheduled 定时 · 需 @EnableScheduling"]
    end

    subgraph P5["阶段5 · Web 暴露"]
        P5map["映射 · @GetMapping / @PostMapping / @RequestMapping"]
        P5bind["入参 · @RequestBody · @PathVariable · @Valid"]
        P5adv["横切 · @RestControllerAdvice · @ExceptionHandler"]
    end

    P0 ==> Boot
    Boot --- BootDecompose
    Boot --> Enable

    BootDecompose -->|"ComponentScan"| P2a
    BootDecompose -->|"扫描 @Configuration"| P2b
    BootDecompose -.->|"AutoConfiguration.imports"| P2c

    Props -.->|"注入到 @Bean 方法 / AutoConfig"| P2b
    Props -.-> P2c

    P2a --> P3
    P2b --> P3
    P2c --> P3

    Enable -.->|"打开对应代理能力"| P4

    P3 --> P4
    P3 --> P5map
    P3 --> P5adv
    P5map --> P5bind

    classDef boot fill:#e8f4fc,stroke:#1a73e8,color:#0d47a1
    classDef core fill:#f5f5f5,stroke:#666,color:#333
    classDef web fill:#e8f5e9,stroke:#2e7d32,color:#1b5e20
    classDef aop fill:#fff3e0,stroke:#ef6c00,color:#e65100

    class Boot,BootDecompose,Enable,P2c boot
    class P0,P2a,P2b,Props,P3 core
    class P4tx,P4async,P4sched aop
    class P5map,P5bind,P5adv web
```
