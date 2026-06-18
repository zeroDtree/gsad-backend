# GSAD Startup Flow

From `docker compose up` through backend readiness: image build, container orchestration, JVM/Spring Boot init, and optional mocks.

---

## Docker Compose startup order

Default:

```bash
docker compose up --build
```

Optional GPU metrics mock:

```bash
docker compose --profile gpu-server-report-mock up --build
```

```mermaid
flowchart TB
    subgraph userLayer [User]
        CMD["docker compose up --build"]
    end

    subgraph buildLayer [Parallel image build]
        B1["backend Dockerfile"]
        B2["account-provision-mock"]
        B3["gpu-server-report-mock profile"]
    end

    subgraph infraLayer [Parallel infra]
        PG["postgres:16 :5432"]
        RD["redis:7 :6379"]
    end

    subgraph healthLayer [After healthchecks]
        BE["backend :8080"]
        AP["account-provision-mock polls pending"]
    end

    subgraph optionalLayer [Optional profile]
        GPU["gpu-server-report-mock every 30s"]
    end

    CMD --> buildLayer
    buildLayer --> infraLayer
    PG -->|"pg_isready"| healthLayer
    RD -->|"redis ping"| healthLayer
    healthLayer --> BE
    BE -->|"service_healthy"| AP
    AP -->|"provision/pending + complete"| BE
    BE -->|"service_started"| optionalLayer
    GPU -->|"POST /api/internal/servers/report"| BE
    BE --> PG
    BE --> RD
```

| Stage | Service | Port | Ready when |
|-------|---------|------|------------|
| 1 | postgres | 5432 | `pg_isready` |
| 1 | redis | 6379 | `PING` ok |
| 2 | backend | 8080 | postgres + redis healthy |
| 3 | account-provision-mock | — | backend healthy; polls every 10s |
| 4 (optional) | gpu-server-report-mock | — | backend healthy; reports every 30s |

---

## Backend image build (Dockerfile)

```mermaid
flowchart LR
    subgraph stage1 [Stage 1 build]
        S1A["maven:3.9-temurin-21"]
        S1B["COPY pom.xml"]
        S1C["mvn dependency:go-offline"]
        S1D["COPY src/"]
        S1E["mvn package -DskipTests"]
        S1A --> S1B --> S1C --> S1D --> S1E
    end

    subgraph stage2 [Stage 2 runtime]
        S2A["temurin:21-jre-alpine"]
        S2B["non-root user gsad"]
        S2C["COPY war to /app/app.war"]
        S2D["java -jar app.war"]
        S2A --> S2B --> S2C --> S2D
    end

    stage1 -->|"COPY --from=build"| stage2
```

Cache: `pom.xml` changes invalidate dependency layer; `src/` changes only re-run `package`.

---

## JVM entry

```mermaid
flowchart TD
    EP["ENTRYPOINT java -jar app.war"]
    MF["META-INF/MANIFEST.MF"]
    SC["Start-Class: GsadApplication"]
    MAIN["GsadApplication.main()"]
    RUN["SpringApplication.run()"]

    EP --> MF --> SC --> MAIN --> RUN
```

`ServletInitializer` is for external Tomcat WAR deploy only; `java -jar` uses `GsadApplication.main()`.

---

## Spring Boot startup (backend container)

```mermaid
flowchart TB
    START["GsadApplication.main()"]

    subgraph bootLayer [Bootstrap]
        A1["application.properties + env"]
        A2["Component scan com.zerodtree.gsad"]
        A3["AutoConfiguration"]
    end

    subgraph dataLayer [Data layer]
        D1["HikariCP postgres:5432/gsad"]
        D2["Flyway V1..V4"]
        D3["Hibernate validate"]
        D4["Redis"]
    end

    subgraph webLayer [Web]
        W1["Tomcat :8080"]
        W2["SecurityFilterChain JWT + PSK"]
        W3["Controllers"]
        W4["SpringDoc"]
    end

    subgraph bgLayer [Background]
        G1["ExpirationScheduler 60s"]
    end

    READY["Started GsadApplication"]

    START --> bootLayer
    A1 --> D1 --> D2 --> D3 --> D4 --> webLayer
    W1 --> W2 --> W3 --> W4 --> bgLayer --> READY
```

### Environment mapping

| Env | Property |
|-----|----------|
| `DB_HOST`, `DB_USER`, `DB_PASSWORD` | `spring.datasource.*` |
| `REDIS_HOST`, `REDIS_PASSWORD` | `spring.data.redis.*` |
| `JWT_SECRET` | `jwt.secret` |
| `AGENT_PSK` | `agent.psk` |

### Flyway on empty database

```mermaid
flowchart TD
    F0["Flyway connects"]
    F1{"Schema exists?"}
    F2["baseline-on-migrate"]
    F3["V1 init"]
    F4["V2 indexes"]
    F5["V3 admin seed"]
    F6["V4 mock servers"]
    F7["Hibernate validate"]
    OK["Data layer ready"]

    F0 --> F1
    F1 -->|no| F2 --> F3 --> F4 --> F5 --> F6 --> F7 --> OK
    F1 -->|behind| F3
    F1 -->|at V4| F7 --> OK
```

---

## Infrastructure services

**postgres:** PG 16, db `gsad`, volume `postgres_data`, healthcheck `pg_isready`.

**redis:** Redis 7, `--requirepass`, healthcheck `redis-cli ping`.

**account-provision-mock:** `provision_loop.py` polls `provision/pending`, completes grants/revokes. Contract: [agent-provision.md](agent-provision.md).

**gpu-server-report-mock** (profile): `report-loop.sh` POSTs to `/api/internal/servers/report` for mock `serverId` values.

---

## Runtime topology

```mermaid
flowchart TB
    User["Browser / frontend dev proxy"]
    Provision["account-provision-mock"]
    Agent["gpu-server-report-mock optional"]

    User -->|"JWT REST :8080"| Backend["backend"]
    Provision -->|"PSK provision API"| Backend
    Agent -->|"PSK report API"| Backend

    Backend --> Postgres[(postgres)]
    Backend --> Redis[(redis)]

    Backend --> Sched["ExpirationScheduler 60s"]
    Sched -->|"log expired ACTIVE count"| Backend
    Provision -->|"pull pendingRevokes"| Backend
```

---

## Verification

| Check | How |
|-------|-----|
| Backend alive | `curl -sf http://localhost:8080/v3/api-docs` (compose healthcheck) |
| List servers | `GET /api/servers` — **requires JWT** (register/login first) |
| Provision mock | `account-provision-mock` logs show grant/revoke complete |
| Swagger | http://localhost:8080/swagger-ui.html |
| Mock GPU count | 30 servers after Flyway (`gpu-mock-001` … `030`) |
| Metrics mock | Profile logs: `[gpu-server-report-mock] reported ...` |

---

## Common failures

| Symptom | Fix |
|---------|-----|
| Port 8080 in use | `docker compose down --remove-orphans` |
| postgres/redis not healthy | Check `.env` and container logs |
| Flyway migration failed | Wipe volume after squash: `docker compose down -v`, then `up --build` |
| Missing secrets | Set `JWT_SECRET`, `DB_PASSWORD`, etc. in `.env` |

---

## File index

| File | Role |
|------|------|
| [../docker-compose.yml](../docker-compose.yml) | Service orchestration |
| [../Dockerfile](../Dockerfile) | Multi-stage backend build |
| [../pom.xml](../pom.xml) | Maven deps and WAR packaging |
| [../src/main/java/com/zerodtree/gsad/GsadApplication.java](../src/main/java/com/zerodtree/gsad/GsadApplication.java) | Main class |
| [../src/main/resources/application.properties](../src/main/resources/application.properties) | Runtime config |
| [../src/main/resources/db/migration/](../src/main/resources/db/migration/) | Flyway scripts |
| [agent-provision.md](agent-provision.md) | Internal provision contract |
| [../dev/account-provision-mock/](../dev/account-provision-mock/) | Dev provisioner mock |
