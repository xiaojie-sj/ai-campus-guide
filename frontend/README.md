# Frontend · Vue 3 校园导览 SPA

本目录是独立的 Vue 3 单页应用，负责三维校园渲染、数字人、公众端对话、精品路线和管理后台。

## 技术栈

- Vue 3.5 + TypeScript 6
- Vite 8
- Vue Router 5
- Three.js 0.187
- Vitest + Vue Test Utils + jsdom

## 环境要求

- Node.js 20+，建议 Node.js 22 LTS
- npm 10+
- 支持 WebGL2 的现代浏览器

## 安装与启动

```powershell
cd frontend
npm ci
npm run dev
```

默认地址：<http://127.0.0.1:5173/>

局域网或手机访问：

```powershell
npm run dev -- --host 0.0.0.0
```

首次没有 `package-lock.json` 时使用 `npm install`；仓库中已有锁文件时应优先使用 `npm ci`。

## 构建与预览

```powershell
npm run build
npm run preview
```

- 构建命令先执行 `vue-tsc -b` 类型检查，再生成 `dist/`。
- `dist/` 是纯静态产物，可独立部署到 Nginx、Caddy、OSS/CDN 或静态托管平台。
- 不要将 `dist/` 复制到 `backend/src/main/resources`；前后端保持独立发布。

## API 连接方式

### 本地开发：Vite 代理

`vite.config.ts` 默认把 `/api` 代理到 `http://localhost:8080`：

```text
Browser → Vite :5173 /api/* → Spring Boot :8080
```

修改代理目标：

```powershell
$env:VITE_API_PROXY_TARGET = "http://localhost:9090"
npm run dev
```

也可以复制 `.env.example` 为 `.env.local` 后修改。`.env.local` 不进入 Git。

### 独立部署：显式 API 地址

```powershell
$env:VITE_API_BASE_URL = "https://api.example.edu.cn"
npm run build
```

`VITE_API_BASE_URL` 在构建时写入前端产物，因此不同环境应分别构建。不要在该变量末尾追加 `/api`，客户端已经使用 `/api/...` 路径。

### 推荐生产方式：同源反向代理

将前端静态文件与 API 放在同一域名下，既不需要设置 `VITE_API_BASE_URL`，也能避免额外 CORS：

```nginx
location / {
    root /srv/campus-guide/frontend;
    try_files $uri $uri/ /index.html;
}

location /api/ {
    proxy_pass http://127.0.0.1:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

## 环境变量

| 变量 | 作用域 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `VITE_API_BASE_URL` | 浏览器构建 | 空 | API 源地址；为空时使用同源 `/api` |
| `VITE_API_PROXY_TARGET` | 仅 Vite 开发服务器 | `http://localhost:8080` | 本地 `/api` 代理目标，不写入浏览器包 |

示例见 [`.env.example`](.env.example)。

## 目录说明

```text
frontend/
├─ public/
│  ├─ models/campus-buildings.glb     校园模型（约 23.99 MB）
│  ├─ media/                          地图等公共素材
│  └─ campus-viewer.html              独立 Three.js 模型预览页
├─ src/
│  ├─ api/client.ts                   fetch 封装、JWT、统一错误处理
│  ├─ components/CampusScene.vue      三维场景与交互
│  ├─ components/AvatarCompanion.vue  数字人组件
│  ├─ components/ChatPanel.vue        问答面板
│  ├─ views/PublicGuide.vue           公众端页面编排
│  ├─ views/AdminDashboard.vue        管理后台
│  ├─ views/AdminLogin.vue            登录页
│  ├─ router.ts                       路由与登录守卫
│  ├─ types.ts                        前后端数据契约
│  └─ *.spec.ts                       组件测试
├─ vite.config.ts                     构建、测试与开发代理
└─ package.json
```

## 测试

```powershell
npm test
```

只监听变更：

```powershell
npx vitest
```

## 常见问题

- **页面提示后端未连接**：确认 `http://127.0.0.1:8080/actuator/health` 正常，且 Vite 代理目标正确。
- **独立域名请求被 CORS 拦截**：在后端设置 `APP_CORS_ORIGIN`，并确保请求来源完全一致（协议、域名、端口）。
- **模型加载慢**：GLB 约 24 MB。生产环境启用 Brotli/gzip、缓存和 CDN；不要在前端长期使用 Vite 开发服务器。
- **修改模型后页面仍是旧模型**：重新运行 `tools/campus_map2/build.py`，并清理浏览器缓存。