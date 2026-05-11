# MedConsensus Frontend Workspace

## 前后端分离结构

- `src/main/java/...` 为 Spring Boot 后端，提供 `/api/workspace/*` REST 接口和 `/ws/diagnosis` WebSocket 端点。
- `frontend/` 为独立 React + Vite 前端，通过代理访问本地后端。

## 本地启动

### 启动后端

```bash
mvn spring-boot:run
```

后端默认端口为 `8086`。

### 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端默认端口为 `5173`，已在 `vite.config.js` 中代理：

- `/api` -> `http://localhost:8086`
- `/ws` -> `http://localhost:8086`

## 当前已实现

- 现代医疗蓝主题的三栏工作台布局
- 用户信息与多会话管理区
- AI 诊断输出区、置信度仪表盘、Reviewer 评审进度
- Human-in-the-loop 医生意见输入区
- REST API 封装与 WebSocket 实时流程推送

## LangSmith 追踪

后端已支持通过 OpenTelemetry 将工作流节点和模型调用发送到 LangSmith。

建议使用环境变量而不是把 key 写进代码或配置文件：

```bash
set LANGSMITH_ENABLED=true
set LANGSMITH_API_KEY=your_langsmith_api_key
set LANGSMITH_PROJECT=MedConsenus
set LANGSMITH_SERVICE_NAME=medconsenus-backend
set LANGSMITH_CAPTURE_CONTENT=false
set LANGSMITH_OTEL_ENDPOINT=https://api.smith.langchain.com/otel/v1/traces
set LANGSMITH_TIMEOUT=60s
set MAVEN_OPTS=-Djava.net.preferIPv4Stack=true
```

说明：

- `LANGSMITH_CAPTURE_CONTENT=false` 时，只发送流程拓扑和元数据，更适合当前医疗场景。
- 如果你确认可以把提示词和部分文本送到 LangSmith，再改成 `true`。

## Docker 部署

部署文件保持在当前目录结构中：

- `docker/deploy/Dockerfile`：构建前端静态资源并打包 Spring Boot 应用
- `docker/deploy/docker-compose.yml`：启动 app、Postgres、Redis、Neo4j，可选 nginx
- `docker/deploy/nginx/medconsensus.conf`：nginx 反向代理配置
- `docker/postgres/init/01-init.sql`：Postgres 首次初始化脚本
- `.dockerignore`：构建镜像时裁剪上下文

### 准备环境变量

编辑 `docker/deploy/.env`，至少填写：

- `API_KEY`
- `MIMO_API_KEY`
- `POSTGRES_PASSWORD`
- `NEO4J_PASSWORD`

### 构建并推送应用镜像

在本地或 CI 环境执行，云服务器不需要执行这一步：

```bash
docker build -f docker/deploy/Dockerfile -t your-registry.example.com/medconsensus:dev .
docker push your-registry.example.com/medconsensus:dev
```

然后在云服务器的 `docker/deploy/.env` 中设置同一个镜像：

```bash
APP_IMAGE=your-registry.example.com/medconsensus:dev
```

### 直接暴露后端端口

```bash
cd docker/deploy
docker compose pull
docker compose up -d
```

默认访问：

- 应用：`http://localhost:8086`
- Postgres：`localhost:5432`
- Redis：`localhost:6379`
- Neo4j Browser：`http://localhost:7474`

### 使用 nginx 反向代理

```bash
cd docker/deploy
docker compose --profile proxy pull
docker compose --profile proxy up -d
```

默认访问：

- nginx：`http://localhost`
- 后端仍可通过 `http://localhost:8086` 访问

### 初始化数据说明

`docker/postgres/init/01-init.sql` 只会在 `postgres_data` volume 第一次创建时执行。脚本会初始化：

- `doctor_basic_info`
- `patient_basic_info`
- `disease_medicine`
- `vector_db`
- `medical_embedding`
- pgvector 扩展

如果已经启动过数据库，又修改了初始化脚本，需要手动执行 SQL，或删除旧 volume 后重新启动。

## 后续建议

- 将 `/api/workspace/simulate` 替换为真实 LangGraph 编排入口
- 将患者资料和会话列表接入数据库实体与 service
- 为会话增删改查补充真实后端接口
