# MedConsensus 部署文档

本文档面向需要从 GitHub 获取项目并完成部署的用户，覆盖镜像拉取、环境变量配置、Docker Compose 启动、健康检查、升级回滚和常见问题排查。

MedConsensus 生产形态是一个内置 React 静态资源的 Spring Boot 应用。推荐使用 Docker Compose 同时启动应用服务、PostgreSQL、Redis、Neo4j 和可选 nginx。

## 1. 部署组件

| 服务 | 作用 | 默认端口 |
| --- | --- | --- |
| `app` | Spring Boot 应用，提供前端页面、REST API 和 WebSocket | `8086` |
| `postgres` | PostgreSQL 数据库，同时承载 pgvector 向量库 | `5432` |
| `redis` | 保存会话列表、对话历史、诊断快照 | `6379` |
| `neo4j` | 保存医学知识图谱 | `7474`、`7687` |
| `nginx` | 可选反向代理，统一代理页面、接口和 WebSocket | `80` |

## 2. 推荐部署方式

| 方式 | 适用场景 | 服务器需要安装 |
| --- | --- | --- |
| 直接拉取已发布镜像 | 演示、评审、普通部署 | Docker、Docker Compose |
| 从源码构建镜像 | 二次开发、自定义镜像、CI | Docker，或 JDK 17 + Maven + Node.js |

大多数部署用户推荐使用“直接拉取已发布镜像”。

## 3. 拉取已发布镜像

当前应用镜像：

```text
crpi-4wwu4n7c8aebuv9g.cn-hangzhou.personal.cr.aliyuncs.com/lofty/medconsensus:dev
```

如果镜像仓库为私有仓库，请先使用已授权账号登录：

```bash
docker login --username=<authorized_username> crpi-4wwu4n7c8aebuv9g.cn-hangzhou.personal.cr.aliyuncs.com
```

然后拉取镜像：

```bash
docker pull crpi-4wwu4n7c8aebuv9g.cn-hangzhou.personal.cr.aliyuncs.com/lofty/medconsensus:dev
```

建议为部署方创建只读拉取账号，不要共享个人主账号密码。

## 4. 服务器准备

服务器需要安装：

- Docker 24+
- Docker Compose v2

服务器需要能访问模型服务地址：

```text
https://dashscope.aliyuncs.com/compatible-mode/v1
https://token-plan-cn.xiaomimimo.com/v1
```

端口放行建议：

| 场景 | 建议放行端口 |
| --- | --- |
| 直接访问应用 | `8086` |
| nginx 反向代理 | `80`，或自行配置 HTTPS 端口 |
| 本地调试数据库 | `5432`、`6379`、`7474`、`7687` |

生产环境不建议公网暴露 PostgreSQL、Redis 和 Neo4j。

## 5. 配置环境变量

从仓库根目录复制示例文件：

```bash
cp docker/deploy/.env.example docker/deploy/.env
```

编辑 `docker/deploy/.env`：

```env
APP_IMAGE=crpi-4wwu4n7c8aebuv9g.cn-hangzhou.personal.cr.aliyuncs.com/lofty/medconsensus:dev
APP_PORT=8086
NGINX_PORT=80

POSTGRES_USER=postgres
POSTGRES_PASSWORD=change_me
POSTGRES_PORT=5432

REDIS_PORT=6379

NEO4J_PASSWORD=change_me
NEO4J_HTTP_PORT=7474
NEO4J_BOLT_PORT=7687

API_KEY=your_dashscope_api_key
MIMO_API_KEY=your_mimo_api_key

LANGSMITH_ENABLED=false
LANGSMITH_API_KEY=
LANGSMITH_PROJECT=MedConsenus
LANGSMITH_SERVICE_NAME=medconsenus-backend
LANGSMITH_CAPTURE_CONTENT=false

JAVA_OPTS=-Xms256m -Xmx768m -Djava.net.preferIPv4Stack=true
```

关键变量说明：

| 变量 | 必填 | 说明 |
| --- | --- | --- |
| `APP_IMAGE` | 是 | 应用镜像地址 |
| `POSTGRES_PASSWORD` | 是 | PostgreSQL 密码 |
| `NEO4J_PASSWORD` | 是 | Neo4j 密码 |
| `API_KEY` | 是 | DashScope 兼容 OpenAI API Key |
| `MIMO_API_KEY` | 否 | MiMo API Key，Treatment Agent 使用 |
| `LANGSMITH_API_KEY` | 否 | 仅开启 LangSmith 追踪时需要 |
| `JAVA_OPTS` | 否 | JVM 参数，例如堆内存和网络栈配置 |

`docker/deploy/.env` 包含敏感信息，禁止提交到 GitHub。

## 6. 启动服务

进入部署目录：

```bash
cd docker/deploy
```

拉取镜像并启动：

```bash
docker compose pull
docker compose up -d
```

查看容器状态：

```bash
docker compose ps
```

查看应用日志：

```bash
docker compose logs -f app
```

默认访问地址：

```text
http://<server-ip>:8086
```

## 7. 使用 nginx 反向代理

Compose 文件内置 `proxy` profile。启用方式：

```bash
cd docker/deploy
docker compose --profile proxy pull
docker compose --profile proxy up -d
```

访问：

```text
http://<server-ip>
```

nginx 配置文件：

```text
docker/deploy/nginx/medconsensus.conf
```

代理规则：

- `/` 代理到 Spring Boot 页面。
- `/api/` 代理到后端 REST API。
- `/ws/` 代理到 WebSocket，并保留 Upgrade 头。

## 8. 首次数据库初始化

PostgreSQL 首次创建 `postgres_data` volume 时，会执行：

```text
docker/postgres/init/01-init.sql
```

初始化内容：

- 创建 `vector_db` 数据库。
- 启用 pgvector 扩展。
- 创建 `medical_embedding` 向量表。
- 创建 `doctor_basic_info`、`patient_basic_info`、`disease_medicine` 等业务表。
- 写入少量疾病-药品示例数据。

应用启动时还会通过 JPA `ddl-auto: update` 维护实体对应表，例如 `final_diagnosis_record`。

注意：初始化 SQL 只会在 PostgreSQL 数据目录为空时执行。数据库已经启动过后，修改初始化 SQL 不会自动重放。

开发环境需要重建数据时：

```bash
cd docker/deploy
docker compose down -v
docker compose up -d
```

生产环境不要直接执行 `down -v`，该命令会删除数据库、Redis 和 Neo4j volume。

## 9. 健康检查

应用健康检查：

```bash
curl http://127.0.0.1:8086/actuator/health
```

正常情况下返回：

```json
{"status":"UP"}
```

常用排查命令：

```bash
docker compose ps
docker compose logs -f app
docker compose logs -f postgres
docker compose logs -f neo4j
```

## 10. 从源码构建镜像

如需自行构建镜像，在仓库根目录执行：

```bash
docker build -f docker/deploy/Dockerfile -t your-registry.example.com/medconsensus:dev .
docker push your-registry.example.com/medconsensus:dev
```

然后修改 `.env`：

```env
APP_IMAGE=your-registry.example.com/medconsensus:dev
```

Dockerfile 使用多阶段构建：

1. Node 20 构建 React 前端静态资源。
2. Maven + JDK 17 打包 Spring Boot Jar。
3. Eclipse Temurin 17 JRE 运行最终应用。

## 11. 升级与回滚

升级应用镜像：

```bash
cd docker/deploy
docker compose pull app
docker compose up -d app
docker compose logs -f app
```

回滚到旧镜像：

1. 将 `.env` 中的 `APP_IMAGE` 改回旧 tag。
2. 重启应用服务：

```bash
docker compose pull app
docker compose up -d app
```

推荐镜像 tag 具备可追溯性：

```text
medconsensus:dev-20260511-001
medconsensus:debug-20260511-001
medconsensus:release-20260511-001
```

## 12. 数据备份

PostgreSQL 备份：

```bash
docker exec medconsensus-postgres pg_dump -U postgres medconsenus > medconsenus.sql
docker exec medconsensus-postgres pg_dump -U postgres vector_db > vector_db.sql
```

Redis 当前主要保存会话型数据，Neo4j 保存知识图谱数据，是否备份取决于部署环境和数据价值。

## 13. 常见问题

### 提示缺少 `API_KEY`

Compose 文件要求 `API_KEY` 必填。检查 `docker/deploy/.env` 是否存在，并确认包含：

```env
API_KEY=your_dashscope_api_key
```

### 镜像拉取失败或提示 unauthorized

检查以下几点：

- 是否登录了正确的镜像仓库。
- 账号是否拥有拉取权限。
- `APP_IMAGE` 的命名空间和 tag 是否正确。

可手动验证：

```bash
docker pull crpi-4wwu4n7c8aebuv9g.cn-hangzhou.personal.cr.aliyuncs.com/lofty/medconsensus:dev
```

### Neo4j 一直 unhealthy

Neo4j 首次启动后会把初始密码写入数据 volume。如果第一次启动后又修改了 `NEO4J_PASSWORD`，旧 volume 里仍然可能使用旧密码。

开发环境可重建：

```bash
docker compose down -v
docker compose up -d neo4j
```

生产环境请先备份数据。

### 修改初始化 SQL 后没有生效

PostgreSQL 的 `/docker-entrypoint-initdb.d` 脚本只在空数据目录首次初始化时执行。已有 volume 时，需要手动执行 SQL 或使用迁移脚本。

### 页面能打开，但接口返回 401

工作台接口依赖医生登录态。请先通过页面登录/注册，或调用 `/api/auth/register`、`/api/auth/login`。

### WebSocket 没有实时进度

确认：

- 前端连接地址为 `/ws/diagnosis`。
- 前端订阅 topic 为 `/topic/pipeline`。
- 如果经过 nginx，`/ws/` 配置包含 WebSocket Upgrade 头。
