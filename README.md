# 湖北工程学院新技术学院 · AI 数字人校园导览系统

基于 **Three.js 三维校园 + 数字人导览 + 私有知识库 RAG** 的校园导览 Web 系统。访客可以漫游校园模型、点击建筑听讲解、询问已审核的校园信息并查看步行导航；管理端可维护点位、路网、路线、机位、知识库、FAQ、日志和系统参数。

> 模型严格依据 [`地图2.png`](docs/assets/maps/地图2.png) 的标注、轮廓与相对位置构建。底图没有标注层数和立面细节，相关部分属于合理推断；未标注的建筑体量按宿舍或配套用房处理。

![校园总览](docs/03-modeling/renders/01_overview_hero.png)

## 架构边界

项目由两个可独立开发、构建和部署的应用组成，不使用“前端构建产物复制进后端”的耦合方式：

```text
Browser
  ├─ 静态 SPA ─────────────── frontend/  (Vue 3 + Vite + Three.js)
  │                              │
  │                              └─ HTTP /api/* ──┐
  └─ 同源反向代理（生产）                         │
                                                   ▼
                                      backend/  (Spring Boot REST API)
                                                   │
                                                   └─ H2 / MySQL / PostgreSQL
```

- **前端**是纯 SPA，构建结果位于 `frontend/dist/`，可部署到任意静态站点或 CDN。
- **后端**只提供 REST API、认证、知识库和路径规划，不承载前端源码或构建产物。
- **本地开发**由 Vite 将 `/api` 代理到 `http://localhost:8080`。
- **独立部署**通过 `VITE_API_BASE_URL` 指向 API 域名，或由 Nginx/Caddy 配置同源 `/api` 反向代理。
- 两端仅通过 JSON API 和 JWT 交互，目录与依赖互不包含。

## 功能概览

- **三维校园**：约 50 万三角面 GLB、建筑点选、标签避让、镜头飞行、路线高亮和程序化兜底场景。
- **数字人导览**：SVG 数字人，支持待机、挥手、指引手势，并随回答和场景动作联动。
- **可信问答**：FAQ → 本地向量检索 → 可选 LLM 组织回答，返回来源引用；没有已审核依据时明确拒答。
- **步行导航**：pgRouting 或内存 A* 路径规划，输出逐段中文指令、距离和预计时间。
- **精品路线**：讲稿自动播放、暂停、上下站，用户提问时暂停并在回答后继续。
- **管理端**：点位、路网、路线、机位、FAQ、知识库、日志、参数与角色权限管理。
- **多数据库档案**：H2 零依赖演示；MySQL；PostgreSQL + PostGIS + pgRouting + pgvector。

## 技术栈

| 层 | 技术 |
| --- | --- |
| 前端 | Vue 3.5 · TypeScript 6 · Vite 8 · Vue Router 5 · Three.js 0.187 · Vitest |
| 后端 | Java 17 · Spring Boot 3.5 · MyBatis-Plus 3.5 · Flyway · JJWT · Apache Tika · Knife4j 4.5 + springdoc 2.8 |
| 数据库 | H2（默认演示）/ MySQL 8 / PostgreSQL 15+ + PostGIS + pgRouting + pgvector |
| AI | 本地 256 维特征哈希向量；可选 OpenAI 兼容对话、ASR、TTS 网关 |
| 建模 | Blender 5.2 · CLI-Anything Blender Harness（外部工具）· glTF/GLB |

## 快速开始

环境要求：JDK 17+、Node.js 20+（建议 22）、npm 10+。默认 `demo` 档案不需要安装数据库。

### 1. 启动后端

```powershell
cd backend
.\mvnw.cmd clean package -DskipTests
java -jar target\campus-guide-1.0.0.jar
```

### 2. 启动前端

另开一个终端：

```powershell
cd frontend
npm ci
npm run dev
```

| 入口 | 地址 |
| --- | --- |
| 公众端 | http://127.0.0.1:5173/ |
| 管理端 | http://127.0.0.1:5173/admin/login |
| 模型预览 | http://127.0.0.1:5173/campus-viewer.html |
| 后端健康检查 | http://127.0.0.1:8080/actuator/health |
| 接口调试文档（Knife4j） | http://127.0.0.1:8080/doc.html |

演示账号为 `admin`、`editor`、`reviewer`、`analyst`，密码均为 `Demo@123`。上线前必须修改密码与 JWT 密钥。

## 前端与后端独立部署

推荐生产结构：

```text
Nginx / Caddy
  ├─ /        → frontend/dist 静态文件（SPA history fallback）
  ├─ /api     → 127.0.0.1:8080
  └─ /models  → 前端静态 GLB，启用 Brotli 与长缓存
```

如果 API 使用独立域名，构建前设置：

```powershell
cd frontend
$env:VITE_API_BASE_URL = "https://api.example.edu.cn"
npm run build
```

后端需同步配置允许来源：

```powershell
$env:APP_CORS_ORIGIN = "https://guide.example.edu.cn"
```

本地开发代理目标也可通过 `VITE_API_PROXY_TARGET` 修改。完整说明见 [`frontend/README.md`](frontend/README.md) 和 [`backend/README.md`](backend/README.md)。

## 目录结构

```text
AIDigitalPersona/
├─ frontend/                      独立 Vue 3 SPA
│  ├─ public/models/              运行时 GLB 与预览页
│  ├─ src/api/                    统一 API 客户端
│  ├─ src/components/             三维场景、数字人、对话
│  ├─ src/views/                  公众端、管理端、登录页
│  └─ README.md                   前端开发与部署说明
├─ backend/                       独立 Spring Boot REST API
│  ├─ src/main/java/              控制器、服务、领域、算法
│  ├─ src/main/resources/        配置、知识资料、Flyway 迁移
│  ├─ src/test/                   后端测试
│  └─ README.md                   后端开发与部署说明
├─ tools/campus_map2/             可复现的校园建模管线
│  ├─ layout.py                   建筑/道路/广场的像素坐标真源
│  ├─ build.py                    Blender 几何、材质、渲染和 GLB 导出
│  ├─ export_backend.py           生成 V8 SQL 与布局清单
│  └─ scripts/                    对比图与网页截图辅助脚本
├─ docs/
│  ├─ 01_requirements/            需求规格说明书
│  ├─ 02-architecture/            项目介绍与技术说明
│  ├─ 03-modeling/                建模说明、渲染图和布局清单
│  ├─ assets/maps/                建模依据与参考地图
│  └─ models/                     可编辑 Blender 工程
├─ scripts/start-optimized.ps1    启动 PostgreSQL 档案的便捷脚本
├─ README.md
├─ .editorconfig
└─ .gitignore
```

`CLI-Anything` **不属于本项目源码**，不会出现在仓库目录中。它是 Codex 的全局能力，安装位置与项目分离：

```text
%USERPROFILE%\.codex\skills\cli-anything       # Codex 识别的 Skill
%USERPROFILE%\.codex\vendor\CLI-Anything        # 可选的上游源码/harness
```

因此不要把 `git clone CLI-Anything` 或 `codex-skill/scripts/install.sh` 执行在项目根目录。建模说明见 [`docs/03-modeling/README.md`](docs/03-modeling/README.md)。

## 三维建模管线

```text
docs/assets/maps/地图2.png
  → tools/campus_map2/layout.py      定义建筑、道路和广场坐标
  → tools/campus_map2/build.py       Blender 生成、渲染、保存、导出
  → frontend/public/models/campus-buildings.glb
  → tools/campus_map2/export_backend.py
  → docs/03-modeling/campus-layout.json
  → backend/src/main/resources/db/migration/postgresql/V8__map2_campus_alignment.sql
```

模型包含 68 个 mesh、44 个材质、503,350 个三角面，GLB 约 23.99 MB。顶视正交图与原始地图保持 1706×1320 同尺寸对齐。

## 测试

```powershell
cd frontend
npm test
npm run build

cd ..\backend
.\mvnw.cmd test -q `
  "-Dspring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
```

## 文档

- [项目介绍（技术栈、功能、接口、算法、部署）](docs/02-architecture/项目介绍.md)
- [需求规格说明书](docs/01_requirements/湖北工程学院新技术学院AI数字人校园导览系统_需求规格说明书_PRD.md)
- [三维建模与渲染说明](docs/03-modeling/README.md)
- [文档索引](docs/README.md)

## 已知限制

- 底图没有层数标注，模型层数与立面为合理推断。
- 演示档案只加载 H2 的 V1–V4 迁移；完整点位/建筑/路网空间数据需使用 `postgres` 档案。
- 默认演示用户转接本地 FAQ/向量检索；远程 LLM、ASR、TTS 需显式配置。
- GLB 体积较大，生产环境建议启用压缩、缓存，后续可增加 Draco/Meshopt 与 LOD。