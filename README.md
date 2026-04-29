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

## 后续建议

- 将 `/api/workspace/simulate` 替换为真实 LangGraph 编排入口
- 将患者资料和会话列表接入数据库实体与 service
- 为会话增删改查补充真实后端接口
