# CloudStorage — 个人云存储（网盘）

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.5+ / Java 17 |
| 安全认证 | Spring Security + JWT (jjwt 0.12) |
| 数据库 | H2 (开发) / PostgreSQL (生产)/MariaDB(生产) |
| ORM | Spring Data JPA + Hibernate |
| 前端框架 | Vite + React 19 + TypeScript |
| 路由 | React Router v7 |
| 样式 | 自定义 CSS（CSS 变量 + 暗色模式） |
| 构建 | Maven + npm |

## 已实现功能

### 多用户系统
- 用户注册 / 登录
- JWT 身份令牌（含用户ID、登录时间、过期时间）
- 基于角色的权限控制（user / admin）
- 启动时自动创建测试账户（admin / test）
- SPA 路由 + 后端 API 双层防护

### 文件管理
- 文件上传 / 下载
- 文件夹（创建 / 进入 / 返回上级）
- 文件重命名 / 删除
- 批量删除
- 多选（checkbox + 全选）
- 文件搜索
- 面包屑导航
- 路径穿越防护 / 用户目录隔离

### 文件分享
- 创建分享链接（可设下载次数限制 + 过期时间）
- 分享码 + 公开下载页（展示文件元数据）
- 复制分享链接
- 删除分享

### 压缩
- 批量选择文件压缩为 ZIP 并下载

### 前端
- 响应式布局（960px 最大宽度）
- 暗色模式自动适配
- 登录/注册表单 + 受保护路由
- 空状态 / 加载状态 / 错误状态展示

## 后续更新计划

| 代号 | 内容 |
|------|------|
| `todo-extension` | 在线压缩（压缩与下载分离）、账号名长度限制、弹窗交互 |
| `todo-admin-registration` | 首位注册用户自动获得管理员权限，或预留账号首次登录强制改密 |
| `todo-register-restrictions` | 注册时用户名/密码长度校验 |
| `todo-online-editor` | 在线文本编辑器（CodeMirror / Monaco），读写文件内容 |
| `todo-tailwind-frontend` | 新建分支用 Tailwind CSS 重写前端样式 |

## 快速开始

### 启动后端
```bash
./mvnw spring-boot:run          # 默认端口 8080，启动后自动初始化测试用户
```

### 开发前端
```bash
cd frontend && npm install && npm run dev   # 端口 5173，代理 /api → 8080
```

### 构建部署
```bash
cd frontend && npm run build     # 输出到 ../src/main/resources/static
./mvnw spring-boot:run           # 前后端一体运行，访问 localhost:8080
```



### Docker启动

```bash
docker compose up -d	# 访问 8080 端口即可
```









### 测试账户

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | 123456 | admin |
| user | 123456 | user |

## 项目结构
```
cloudstorage/
  ├── src/main/java/com/cloudstorage/
  │   ├── config/        — SecurityConfig, DataInitializer, AppConfig
  │   ├── controller/    — Auth, File, ShareLink, Spa
  │   ├── model/entity/  — User, UserFile, ShareLink
  │   ├── model/dto/     — 请求/响应 DTO
  │   ├── repository/    — Spring Data JPA 接口
  │   ├── service/       — 业务逻辑
  │   ├── security/      — JwtAuthenticationFilter
  │   └── util/          — JwtTokenUtil, FileUtils, RandomChar
  └── frontend/src/
      ├── api/           — client.ts (fetch 封装)
      ├── components/    — Layout.tsx
      ├── pages/         — Login, Register, Files, Share, ShareAccess
      ├── types/         — TS 接口定义
      └── utils/         — format.ts
```
