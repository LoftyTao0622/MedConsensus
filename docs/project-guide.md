# MedConsensus 项目说明文档

MedConsensus 是一个面向医疗问诊辅助场景的多 Agent 共识诊断系统。系统接收患者主诉和补充信息，由信息收集 Agent、诊断 Agent、多个 Reviewer、决策层和医生复核环节共同生成辅助诊断意见，并在医生确认后生成最终诊断记录和治疗建议。

> 本系统定位为医生辅助工具，不应作为患者自诊或自动处方系统使用。最终诊断和用药建议必须由具备资质的医生确认。

## 1. 技术栈

后端：

- Java 17
- Spring Boot 3.3.0
- Spring Web / WebSocket / Validation / Actuator
- Spring Data JPA
- PostgreSQL + pgvector
- Redis
- Neo4j Java Driver
- Spring AI OpenAI Starter
- LangGraph4j 1.5.14
- OpenTelemetry + LangSmith 可观测追踪

前端：

- React 18
- Vite 5
- STOMP WebSocket
- lucide-react 图标

部署：

- Docker 多阶段构建
- Docker Compose
- 可选 nginx 反向代理

## 2. 目录结构

```text
MedConsensus
├── docker
│   ├── deploy
│   │   ├── Dockerfile
│   │   ├── docker-compose.yml
│   │   ├── .env
│   │   └── nginx/medconsensus.conf
│   └── postgres/init/01-init.sql
├── frontend
│   ├── src
│   │   ├── api
│   │   ├── components
│   │   ├── App.jsx
│   │   └── styles.css
│   └── vite.config.js
├── src/main/java/com/zyt/medconsensus
│   ├── agent
│   ├── config
│   ├── controller
│   ├── dto
│   ├── entity
│   ├── graph
│   ├── graphkg
│   ├── importer
│   ├── llm
│   ├── mapper
│   ├── observability
│   ├── rag
│   ├── service
│   └── tool
├── src/main/resources
│   ├── application.yml
│   └── static
└── pom.xml
```

## 3. 核心模块

| 模块 | 说明 |
| --- | --- |
| `agent` | Collector、Diagnosis、Reviewer、Treatment 等大模型 Agent |
| `graph` | LangGraph 工作流状态对象 |
| `service.impl.CollectorAgentServiceImpl` | 编排诊断和医生复核，将完整会话保存到 PostgreSQL，用 Redis 锁协调并发提交 |
| `llm.MultiModelGateway` | 多模型调用网关，按不同 Agent 配置模型、base URL、temperature |
| `tool.MedicalWorkflowTools` | 风险评估、信息充分性判断、投票、治疗关键词抽取等工具逻辑 |
| `graphkg` | Neo4j 医学知识图谱抽取、写入与推理查询 |
| `rag` / `importer` | pgvector 向量库配置、Embedding 调用、JSON 医疗数据导入 |
| `observability` | LangSmith / OpenTelemetry 追踪 |
| `controller` | 认证、工作台 API、SPA 路由转发 |
| `frontend/src` | 医生工作台前端页面和 API 封装 |

## 4. 业务流程

```mermaid
flowchart TD
    A["医生登录"] --> B["选择或创建患者"]
    B --> C["提交主诉/病情信息"]
    C --> D["Collector Agent 整理病情"]
    D --> E{"信息是否充分"}
    E -- "不充分" --> F["追问补充信息"]
    E -- "充分" --> G["Diagnosis Agent 生成初诊"]
    G --> H["Neo4j 知识图谱补充证据"]
    H --> I["Qwen / Kimi / GLM Reviewer 并行评审"]
    I --> J["Decision Layer 投票、置信度、风险控制"]
    J --> K{"是否需要人工复核"}
    K -- "需要" --> L["医生 Human-in-the-loop 审核"]
    K -- "可输出初诊" --> M["生成 AI 初诊建议"]
    L --> N["保存最终诊断记录"]
    N --> O["Treatment Agent 生成治疗建议"]
```

LangGraph 节点位于 `CollectorAgentServiceImpl`：

```text
collect -> assess -> ask_more_info / diagnose -> review -> decide -> human_review / finalize
```

## 5. 数据存储

PostgreSQL 主要表：

| 表 | 说明 |
| --- | --- |
| `doctor_basic_info` | 医生账号、手机号、科室、职称、密码哈希 |
| `patient_basic_info` | 医生名下患者基础信息 |
| `disease_medicine` | 疾病和药品建议基础库 |
| `final_diagnosis_record` | 医生复核后的最终诊断和治疗建议 |
| `consultation_session` | 按医生和会话保存患者档案关联、问诊消息及最新诊断；归档后保留详情 |
| `report_publication` | 每次发布时保存的报告版本，患者从这里读取报告和解释依据 |
| `medical_evidence_source`、`publication_evidence` | 上传原件、识别范围、原文定位、校正内容及各报告版本采用的资料 |
| `report_follow_up` | 关联报告版本的复诊日期、待补检查、患者反馈和医生查看状态 |

pgvector 库：

| 库/表 | 说明 |
| --- | --- |
| `vector_db.medical_embedding` | 医疗语料切片、metadata、1024 维 embedding |

升级时，服务会尝试从以下旧 Redis key 迁移尚存的会话数据。已经过期的消息无法恢复。患者长期画像从新档案编号开始记录，原有按姓名存放的文件保留在原处，系统不会自动将它们归入同名档案。

```text
medconsenus:collector:sessions:{userId}
medconsenus:collector:memory:{userId}:{sessionId}
medconsenus:collector:diagnosis:{userId}:{sessionId}
```

Neo4j：

- 存储医学实体和关系。
- 由 `MedicalKnowledgeGraphRepository` 查询症状、疾病、治疗路径。
- 当前推理服务在异常时会返回空结果，不阻断主诊断流程。

## 6. 后端接口

认证接口：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/auth/register` | 注册医生或患者账号，必填 `role`（`DOCTOR` 或 `PATIENT`） |
| `POST` | `/api/auth/login` | 使用所选角色、手机号和密码登录，角色缺失或无效时返回 400 |
| `GET` | `/api/auth/me` | 获取当前会话的账号。缺少有效身份时返回 401，需要重新登录 |
| `POST` | `/api/auth/logout` | 退出登录 |

工作台接口：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/workspace/patients` | 查询当前医生患者列表 |
| `POST` | `/api/workspace/patients` | 新增患者 |
| `PUT` | `/api/workspace/patients/{patientId}` | 更新患者 |
| `DELETE` | `/api/workspace/patients/{patientId}` | 删除患者 |
| `GET` | `/api/workspace/sessions` | 查询会话列表 |
| `GET` | `/api/workspace/sessions/{sessionId}` | 查询会话详情 |
| `DELETE` | `/api/workspace/sessions/{sessionId}` | 将会话从工作台列表归档，保留已发布报告和历史消息 |
| `GET` | `/api/workspace/diagnosis` | 获取最近诊断结果 |
| `POST` | `/api/workspace/consultations` | 提交咨询内容，触发多 Agent 诊断流程 |
| `POST` | `/api/workspace/doctor-review` | 提交医生复核意见并生成最终记录 |
| `POST` | `/api/workspace/simulate` | 演示诊断流程推送 |

WebSocket：

| 项 | 值 |
| --- | --- |
| STOMP Endpoint | `/ws/diagnosis` |
| Topic | `/topic/pipeline` |
| 用途 | 推送 Collector、Diagnosis、Reviewer、Decision、Human Review 等流程进度 |

## 7. 前端说明

医生在“医患协作”的问诊待办中按待核资料、待复核、待发布或待看随访筛选。确认检查资料前可以打开原件，核对识别页与检验数值，保存校正后再送入诊断。无法定位的条目会显示相应提示。

医生发布报告时可勾选“安排复诊随访”，填写复诊日期和待补检查。患者在该报告下提交检查结果或病情变化，医生在“待看随访”中对照原报告并确认已阅。修订报告需要重新发布，原随访仍对应创建时的报告版本。

前端是 React + Vite 单页应用，主要模块：

| 文件 | 说明 |
| --- | --- |
| `frontend/src/App.jsx` | 应用主状态与布局入口 |
| `frontend/src/api/auth.js` | 登录、注册、当前用户、退出 |
| `frontend/src/api/workspace.js` | 患者、会话、诊断、医生复核 API |
| `frontend/src/api/websocket.js` | STOMP WebSocket 连接与进度订阅 |
| `frontend/src/components/AuthPanel.jsx` | 登录注册面板 |
| `frontend/src/components/Sidebar.jsx` | 患者和会话侧栏 |
| `frontend/src/components/DiagnosticPanel.jsx` | AI 诊断结果展示 |
| `frontend/src/components/DoctorPanel.jsx` | 医生复核意见输入 |
| `frontend/src/components/SessionDetailPanel.jsx` | 历史会话详情 |

开发环境通过 Vite 代理访问后端。生产环境前端静态资源由 Spring Boot 托管，因此只需要访问 `app` 服务或 nginx。

## 8. 模型配置

模型配置集中在 `application.yml` 的 `spring.ai.openai` 下：

| 配置段 | 当前职责 |
| --- | --- |
| `chat.options` | 主诊断模型 Diagnosis Agent |
| `collector` | 信息收集、病情整理 |
| `reviewers.qwen` | Qwen Reviewer |
| `reviewers.kimi` | Kimi Reviewer |
| `reviewers.glm` | GLM Reviewer |
| `decision` | 决策层路由判断 |
| `treatment` | 医生确认后生成治疗建议 |

生产环境通过 `API_KEY` 和 `MIMO_API_KEY` 注入密钥，不建议在 `application.yml` 中写真实 key。

## 9. 可观测性

项目支持 LangSmith 追踪，默认关闭。开启方式：

```text
LANGSMITH_ENABLED=true
LANGSMITH_API_KEY=your_langsmith_key
LANGSMITH_PROJECT=MedConsenus
LANGSMITH_CAPTURE_CONTENT=false
```

医疗场景建议保持：

```text
LANGSMITH_CAPTURE_CONTENT=false
```

这样只发送流程和元数据，避免把患者文本和提示词内容发送到外部追踪平台。

## 10. 开发约定

建议新增功能时遵循以下边界：

- Controller 只处理 HTTP 入参、Session 和响应包装。
- 业务编排放在 Service，尤其是诊断工作流相关逻辑。
- 大模型调用统一走 `MultiModelGateway`。
- 新增数据库表时优先补充 JPA Entity，同时为 Docker 初始化或迁移脚本补齐 SQL。
- PostgreSQL 持久保存完整问诊；模型读取最近 40 条上下文，Redis 仍用于锁与患者报告解释缓存。
- 医疗建议相关文案要保留医生复核语义，避免表现为自动诊断或自动处方。

## 11. 已知注意点

- 项目配置名中多处使用 `MedConsenus` 拼写，这是当前代码和 Redis key 的既有命名，改名会影响配置、日志、key 和部署变量，需单独规划。
- `docker/deploy/.env` 是本地敏感配置文件，不要提交。
- `docker/postgres/init/01-init.sql` 只在 PostgreSQL volume 首次创建时执行。
- Flyway 管理 `final_diagnosis_record` 等业务表的结构，Hibernate 在启动时校验映射。
- Neo4j 推理失败会降级为空证据，不会中断诊断流程。

