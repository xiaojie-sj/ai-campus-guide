# Backend · Spring Boot Campus Guide API

本目录是独立的 Spring Boot REST API，为三维校园前端提供点位、路线、导航、问答、知识库和管理能力。后端不包含 Vue 源码，也不负责托管 `frontend/dist`。

## 技术栈

- Java 17
- Spring Boot 3.5
- MyBatis-Plus 3.5
- Flyway
- JJWT 0.12
- Apache Tika
- H2 / MySQL / PostgreSQL + PostGIS + pgRouting + pgvector

## 环境要求

- JDK 17+
- 仓库自带的 Maven Wrapper（无需全局安装 Maven）
- 默认 `demo` 档案无需外部数据库

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

## 数据库档案

| Profile | 数据库 | Flyway 目录 | 用途 |
| --- | --- | --- | --- |
| `demo`（默认） | H2 文件库 `./data/campus-guide` | `db/migration/default` V1–V4 | 零依赖演示与开发 |
| `mysql` | MySQL 8 | 默认迁移目录 | 中小规模部署 |
| `postgres` | PostgreSQL + PostGIS | `db/migration/postgresql` V1–V8 | 完整空间数据、pgRouting、pgvector |

### H2 演示档案

```powershell
java -jar target\campus-guide-1.0.0.jar
```

H2 控制台：<http://127.0.0.1:8080/h2-console>

JDBC URL：`jdbc:h2:file:./data/campus-guide`

用户名：`sa`，密码为空。H2 数据目录已被 `.gitignore` 排除。

### PostgreSQL 完整档案

需要 PostgreSQL 15+、PostGIS、pgRouting、pgvector。仓库包含便捷启动脚本：

```powershell
.\..\scripts\start-optimized.ps1
```

只启动数据库：

```powershell
.\scripts\start-postgres.ps1
```

手动运行：

```powershell
$env:POSTGRES_URL = "jdbc:postgresql://localhost:55432/campus_guide"
$env:POSTGRES_USER = "postgres"
$env:POSTGRES_PASSWORD = "123456"
java -jar target\campus-guide-1.0.0.jar --spring.profiles.active=postgres
```

缺少 pgRouting 时，路径规划自动回退到 Java 内存 A*。

### MySQL 档案

先创建 `campus_guide` 数据库，再通过命令行覆盖连接参数：

```powershell
java -jar target\campus-guide-1.0.0.jar `
  --spring.profiles.active=mysql `
  --spring.datasource.url="jdbc:mysql://localhost:3306/campus_guide?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false" `
  --spring.datasource.username=root `
  --spring.datasource.password=your-password
```

## 环境变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | HTTP 端口 |
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
│  ├─ application.yml    多 profile 配置
│  ├─ demo-knowledge/    初始公开知识资料
│  └─ db/migration/      default / postgresql 两套 Flyway 迁移
├─ src/test/java/        单元与上下文测试
├─ scripts/              PostgreSQL 启动辅助脚本
└─ pom.xml
```

## 数据库迁移

- Flyway 在启动时自动执行迁移，禁止修改已在共享环境执行过的历史脚本。
- 新变更应新增版本号更高的迁移文件。
- `default` 与 `postgresql` 目录是两套独立迁移链，schema 特性不同。
- 地图 2 的完整点位、建筑轮廓、路网和相机数据位于 `V8__map2_campus_alignment.sql`。
- 重新生成 V8 与布局清单：在项目根目录运行 `python tools/campus_map2/export_backend.py`。

## 测试

后端运行时会锁定 H2 文件，因此测试应使用独立内存库：

```powershell
cd backend
.\mvnw.cmd test -q `
  "-Dspring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
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