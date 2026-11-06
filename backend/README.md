# Backend · Spring Boot Campus Guide API

本目录是独立的 Spring Boot REST API，为三维校园前端提供点位、路线、导航、问答、知识库和管理能力。后端不包含 Vue 源码，也不负责托管 `frontend/dist`。

## 技术栈

- Java 17
- Spring Boot 3.5
- MyBatis-Plus 3.5
- Flyway
- JJWT 0.12
- Apache Tika
- PostgreSQL + PostGIS + pgRouting + pgvector（H2 仅 test 作用域）

## 环境要求

- JDK 17+
- 仓库自带的 Maven Wrapper（无需全局安装 Maven）
- PostgreSQL 14+（仓库附便携版启动脚本）

## 构建与启动

```powershell
cd backend
.\mvnw.cmd clean package -DskipTests
java -jar target\campus-guide-1.0.0.jar
```

开发模式可直接运行：

```powershell
.\mvnw.cmd spring-boot:run
```

默认端口为 `8080`。健康检查：<http://127.0.0.1:8080/actuator/health>

## 接口文档（Knife4j）

启动后访问 <http://127.0.0.1:8080/doc.html> 进行在线接口调试。

- **公开接口**分组：校园数据、问答、语音和认证接口，无需 JWT。
- **管理接口**分组：点位、路网、机位、路线、FAQ、知识库、参数、统计和审计接口。
- **JWT 调试流程**：先调用 `POST /api/auth/login`，复制响应中的 `data.token`，点击 Knife4j 右上角 **Authorize** 填入 token，再调用 `/api/admin/**`。无需手动添加 Bearer 前缀。
- **OpenAPI JSON**：`/v3/api-docs`（全部）、`/v3/api-docs/public`（公开）、`/v3/api-docs/admin`（管理）。
- 演示账号：`admin`、`editor`、`reviewer`、`analyst`，密码均为 `Demo@123`。

生产环境设置环境变量 `KNIFE4J_ENABLED=false`，会同时关闭 OpenAPI 生成和 `/doc.html` 调试入口。

## 数据库

PostgreSQL（含 PostGIS / pgRouting / pgvector）是唯一运行时数据库；H2 仅以 test 作用域服务于单元测试。

便携版启动（默认 55432 端口）：

```powershell
.\scripts\start-postgres.ps1
```

或整体启动（数据库 + 后端 + 前端）：

```powershell
..\scripts\start-all.ps1
```

手动指定连接：

```powershell
$env:POSTGRES_URL = "jdbc:postgresql://localhost:55432/campus_guide"
$env:POSTGRES_USER = "postgres"
$env:POSTGRES_PASSWORD = "123456"
java -jar target\campus-guide-1.0.0.jar
```

启动时 `DatabaseCapabilities` 探测 PostGIS / pgRouting 是否可用：缺少 pgRouting 时路径规划自动回退 Java 内存 A*。

> **双实例约定（本机）**：项目只使用便携版 PostgreSQL（55432）；D 盘安装的服务版（5432）供其他用途，不要在其上创建 campus_guide 库或执行迁移。迁移请勿在 Navicat 中手工跑，Flyway 会在后端启动时自动应用。

## 环境变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | HTTP 端口 |
| `KNIFE4J_ENABLED` | `true` | 是否启用 Knife4j/OpenAPI 接口文档，生产环境建议设为 `false` |
| `APP_JWT_SECRET` | 演示密钥 | JWT 签名密钥，生产环境必须替换为至少 32 字符的随机值 |
| `APP_JWT_TTL_MINUTES` | `480` | Token 有效期（分钟） |
| `APP_UPLOAD_DIR` | `./data/uploads` | 知识库上传目录 |
| `APP_MAX_FILE_BYTES` | `52428800` | 单文件上限（50 MB） |
| `APP_CORS_ORIGIN` | `http://localhost:5173` | 允许的前端来源 |
| `APP_CORS_ORIGIN_ALT` | `http://127.0.0.1:5173` | 第二个允许来源 |
| `POSTGRES_URL` | `jdbc:postgresql://localhost:55432/campus_guide` | PostgreSQL 地址 |
| `POSTGRES_USER` | `postgres` | PostgreSQL 用户 |
| `POSTGRES_PASSWORD` | `123456` | PostgreSQL 密码 |
| `AI_ENABLED` | `false` | 是否启用远程 AI |
| `AI_BASE_URL` | `https://api.openai.com/v1` | OpenAI 兼容网关 |
| `AI_API_KEY` | 空 | API Key |
| `AI_CHAT_MODEL` | `gpt-4.1-mini` | 对话模型 |
| `AI_ASR_MODEL` | `whisper-1` | 语音识别模型 |
| `AI_TTS_MODEL` | `gpt-4o-mini-tts` | 语音合成模型 |
| `AI_TTS_VOICE` | `alloy` | 合成音色 |
| `AI_TIMEOUT_SECONDS` | `45` | AI 请求超时 |

密钥只通过环境变量或部署平台 Secret 注入，不要提交到 Git。

## API 概览

所有业务响应使用统一信封：

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

### 公开接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/public/bootstrap` | 点位、机位、路线、公开配置一次性加载 |
| `GET` | `/api/public/points` | 公开点位 |
| `GET` | `/api/public/tours` | 公开精品路线 |
| `POST` | `/api/public/chat` | 意图识别、问答、场景指令和路线规划 |
| `POST` | `/api/public/feedback` | 回答反馈 |
| `POST` | `/api/public/asr` | 语音识别，multipart 上传 |
| `POST` | `/api/public/tts` | 语音合成，返回 `audio/mpeg` |

### 认证接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/auth/login` | 管理端登录并签发 JWT |

演示用户：`admin`、`editor`、`reviewer`、`analyst`，密码均为 `Demo@123`。上线前必须修改。

### 管理接口

统一前缀：`/api/admin`，需要 `Authorization: Bearer <token>`。

| 资源 | 路径 |
| --- | --- |
| 点位 | `GET/POST /points`、`PUT/DELETE /points/{id}` |
| 路网 | `GET /roads`、`POST/DELETE /roads/nodes`、`POST/DELETE /roads/edges` |
| 机位 | `GET/POST /cameras`、`PUT /cameras/{id}` |
| 路线 | `GET/POST /tours`、`PUT /tours/{id}`、`GET /tours/{id}/stops` |
| FAQ | `GET/POST /faqs`、`PUT/DELETE /faqs/{id}` |
| 知识库 | `GET/POST /knowledge`、`PUT /knowledge/{id}/publish`、`POST /knowledge/{id}/reindex`、`DELETE /knowledge/{id}` |
| 参数 | `GET /configs`、`PUT /configs/{key}` |
| 统计与审计 | `GET /stats`、`GET /chat-logs`、`GET /audit-logs` |

具体请求字段以 `src/main/java/com/hbeu/campusguide/dto/` 为准。

## 代码结构

```text
backend/
├─ src/main/java/com/hbeu/campusguide/
│  ├─ controller/        REST 控制器
│  ├─ service/           业务编排、RAG、导航、知识入库
│  ├─ algorithm/         意图识别、A*、向量、文本分片
│  ├─ domain/            数据模型
│  ├─ dto/               请求/响应契约
│  ├─ mapper/            MyBatis-Plus Mapper
│  ├─ ai/                可选 AI 网关
│  ├─ config/            JWT、CORS、拦截器、配置
│  └─ common/            统一响应、异常、TraceId
├─ src/main/resources/
│  ├─ application.yml    单一数据源配置（环境变量覆盖）
│  ├─ demo-knowledge/    初始公开知识资料
│  └─ db/migration/      Flyway 迁移 V1–V8（PostgreSQL 唯一真源）
├─ src/test/java/        单元与上下文测试
├─ scripts/              PostgreSQL 启动辅助脚本
└─ pom.xml
```

## 数据库迁移

- Flyway 在启动时自动执行迁移，禁止修改已在共享环境执行过的历史脚本。
- 新变更应新增版本号更高的迁移文件。
- 迁移链唯一：V1–V8 顺序执行，V5 起依赖 PostGIS / pgvector / pgRouting。
- 地图 2 的完整点位、建筑轮廓、路网和相机数据位于 `V8__map2_campus_alignment.sql`。
- 重新生成 V8 与布局清单：在项目根目录运行 `python tools/campus_map2/export_backend.py`。

## 测试

测试通过 `test` profile 使用 H2 内存库并跳过 Flyway（见 `src/test/resources/application-test.yml`），无需本地数据库：

```powershell
cd backend
.\mvnw.cmd test
```

只运行单个测试类：

```powershell
.\mvnw.cmd test -Dtest=PathPlannerTest
```

## 生产注意事项

- 设置 HTTPS、强随机 `APP_JWT_SECRET`，修改所有演示账号密码。
- 配置 `APP_CORS_ORIGIN` 为正式前端域名；同源反向代理时可收紧 CORS。
- 持久化 `APP_UPLOAD_DIR` 和数据库卷，并配置备份。
- Actuator 仅暴露 `health`、`info`，管理接口建议限制在内网或网关后。
- 如果使用外部 AI，配置请求超时、配额、日志脱敏和内容审核。
- 前端与后端各自构建、各自发布；后端不应依赖 `frontend/dist` 是否存在。