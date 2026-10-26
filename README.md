# CloudStorage — 个人云存储（网盘）

一个面向个人 / 小团队的多用户网盘应用。后端使用 Spring Boot 提供 REST API 并托管前端静态资源，前端使用 React + TypeScript。文件的层级结构保存在 MariaDB，文件内容保存在本地磁盘；磁盘位置由文件树实时推导，通过「数据库与磁盘一致性协议 + 启动检查」维护两者一致。

- 接口文档：[docs/api.md](docs/api.md)
- 架构决策记录：[docs/adr/](docs/adr/)
- 需求说明：[ProjectRequirements.md](ProjectRequirements.md)

## 功能概览

### 账户与认证

- 注册、登录、注销、查询当前用户
- 密码使用 BCrypt 存储
- 登录 / 注册成功后签发 JWT，写入 **HttpOnly Cookie**（默认名 `CS_TOKEN`），有效期 2 小时，`SameSite=Strict`
- 角色：`user` / `admin`；`/admin/**` 已预留管理员授权规则
- 密码找回：
  - 邮箱方式：调用 `mail-service-go` 发送重置链接；邮件服务不可达时把链接输出到日志
  - 密保方式：读取密保问题，答对至少 75% 后签发重置 Token
- 启动时自动创建 `admin` / `user` 测试账户

### 文件管理

- 上传（multipart，单文件 / 单请求默认上限 500 MB）
- 下载、目录列表、文件元数据查询
- 新建文件夹、重命名、移动、删除、批量删除
- 文件名搜索（包含匹配，跨目录）
- 前端多选、批量删除 / 批量压缩（单文件下载）
- 在线压缩：把选中的文件 / 文件夹打包为 ZIP，存回当前网盘目录
- 每用户独立存储目录与配额（默认 1 GB），上传与压缩时校验
- 路径穿越防护、所有权校验、上传文件去执行权限

### 文件分享

- 为文件生成分享码（UUID 前 8 位 + `-` + 6～8 位随机字符）
- 可设置下载次数上限（`-1` 表示不限）与过期时间（小时）
- 公开分享信息接口与下载接口，无需登录
- 同一用户对同一文件只允许一个分享链接
- 分享列表、删除分享
- 删除文件时关联分享链接自动删除

### 一致性与安全

- 数据库是文件树的唯一事实来源，行内只保存文件名与父目录 ID
- 逻辑路径按 `parentFolderId` 链实时推导；磁盘路径由逻辑路径 + 用户根目录推导
- 上传使用「临时文件 → 原子改名 → 数据库事务」；重命名 / 移动先改数据库再动磁盘；删除先删数据库、磁盘尽力清理
- 启动后台一致性检查：数据库 ↔ 磁盘双向比对，检测缺失、类型不符、大小偏差、孤儿文件、目录环、重复逻辑路径等，结果写入异常表
- 用户目录 `0700`、文件 `0600`，上传 / 压缩后移除执行位
- 下载以 `application/octet-stream` + `attachment` 返回，不在 Web 根目录中执行

## 技术栈

| 层级 | 技术 |
| --- | --- |
| 后端 | Spring Boot 4.1.0、Java 17、Maven Wrapper |
| Web / 安全 | Spring MVC、Spring Security（无状态）、jjwt 0.12.6 |
| 认证凭据 | JWT + HttpOnly Cookie（`CS_TOKEN`，2 小时，SameSite=Strict） |
| 持久化 | Spring Data JPA / Hibernate、MariaDB 11（H2 驱动保留作备选） |
| 缓存 / 临时令牌 | Redis 7（密码重置 Token） |
| 邮件 | `mail-service-go`（Go HTTP 微服务，独立部署） |
| 前端 | React 19、TypeScript 6、Vite 8、React Router 7 |
| 样式 | 原生 CSS + CSS 变量（自动暗色模式） |
| 构建 / 部署 | Maven Wrapper + npm；前后端一体 JAR / Docker Compose |

## 架构说明

### 请求与认证

```text
Browser（React SPA）
   │  fetch /api/*（开发代理）/ 同源请求（生产）
   ▼
JwtAuthenticationFilter ── 读取 CS_TOKEN Cookie，校验签名与 type=auth
   ▼
Spring MVC Controller
   ├─ AuthController / ForgotPasswordController
   ├─ FileController ──► FileService ──► StorageService ──► STORAGE_ROOT/{userId}/...
   └─ ShareLinkController ──► ShareLinkService ──► StorageService
   ▼
Spring Data JPA ──► MariaDB
Redis（密码重置 Token）      mail-service-go（邮件）
```

1. 登录 / 注册成功后，`AuthCookieService` 生成 JWT 并写入 `Set-Cookie`：`HttpOnly`、`SameSite=Strict`、`Path=/`、`Max-Age` 与 JWT 过期时间一致。
2. JWT 声明包含 `id`、`username`、`role`、`type=auth`、签发时间与过期时间。
3. 后续请求由 `JwtAuthenticationFilter` 解析 Cookie，把用户 ID 作为 principal、`ROLE_USER` / `ROLE_ADMIN` 作为权限放入 `SecurityContext`。
4. 服务端不保存会话（`STATELESS`）；注销由 `GET /logout` 清除 Cookie 并返回 302 跳转 `/login`。
5. 未认证返回 401，已认证但权限不足返回 403；CSRF 防护依赖 `SameSite=Strict`，未启用 CSRF Token。

### 文件树与磁盘路径

- `files` 表是文件树的事实来源；每行保存 `filename` 与 `parentFolderId`，不保存完整路径。
- 根目录的 `parentFolderId` 固定为 `0`；同一用户、同一父目录、同一文件名由数据库唯一约束兜底。
- **逻辑路径**由 `StorageService.resolveLogicalPath()` 沿父目录链实时拼接，重命名 / 移动文件夹只需修改自身一行。
- **磁盘路径** = `STORAGE_ROOT / {userId} / 逻辑路径`。`StorageService.validatePath()` 对路径做 `normalize()`，并校验结果仍以用户根目录开头，阻断 `../`、绝对路径等穿越方式。
- 详见 [ADR 0001：路径由文件树推导](docs/adr/0001-path-derived-from-tree.md)。

### 数据库与磁盘一致性

数据库有事务，文件系统没有，二者无法两阶段提交。项目按操作类型确定写入顺序，并让所有崩溃窗口的残留落在「磁盘孤儿」一侧：

| 操作 | 顺序 | 失败补偿 |
| --- | --- | --- |
| 上传 / 压缩 | 写临时文件 → 原子改名到最终路径 → 数据库事务（含配额） | 尽力删除已写文件 |
| 重命名 / 移动 | 先改数据库自身一行 → 磁盘 `move` | 磁盘失败时回滚数据库 / 记录日志 |
| 删除 | 先删数据库记录（含分享）→ 磁盘尽力删除 | 清不掉的残留记录为异常 |

启动时 `StartupConsistencyChecker` 在后台线程执行双向检查：

- 数据库 → 磁盘：文件缺失、文件 / 文件夹类型不符、大小偏差超过 1 KB 容差
- 磁盘 → 数据库：孤儿文件、上传失败残留的 `.tmp---*.tmp`
- 文件树结构：父目录缺失、父节点不是文件夹、目录成环、同用户重复逻辑路径
- 首次发现异常后等待约 10 秒复核，确认后写入 `FileAnomaly` 表，供后续管理员功能审查

详见 [ADR 0002：数据库与磁盘一致性协议](docs/adr/0002-db-disk-consistency-protocol.md)。

### 文件安全

- 用户目录权限 `rwx------`，普通文件 `rw-------`；上传和压缩完成后移除执行权限。
- 所有文件操作都按 `(fileId, owner)` 查询，先校验归属再操作。
- 逻辑路径推导与磁盘路径校验集中在 `StorageService`，避免各入口重复实现。
- 分享接口只暴露分享码和文件元数据，不暴露磁盘路径；下载必须命中有效的分享码。

### 分享与密码找回

- **分享码**：`ShareLink.verificationCode` 全局唯一；创建时校验下载次数并计算过期时间；公开访问 `GET /Share/{code}` 时先检查过期与次数上限，再递增下载计数。
- **邮箱找回**：先签发 20 分钟的「找回流程 JWT」，再生成 UUID 重置 Token 存入 Redis（默认 20 分钟），通过 `mail-service-go` 发送 `/forgot-password/reset?token=...` 链接。
- **密保找回**：找回流程 JWT 换取问题列表；答案使用 BCrypt 校验，答对至少 75% 后签发 Redis 重置 Token。

## 数据模型

| 表 | 实体 | 关键字段 | 说明 |
| --- | --- | --- | --- |
| `users` | `User` | `username`、`password`、`email`、`role`、`storageQuota`、`storageUsed`、`enabled` | 用户与配额；默认配额 1 GB |
| `files` | `UserFile` | `filename`、`fileSize`、`contentType`、`isFolder`、`parentFolderId`、`owner_id` | 文件树节点；`parentFolderId=0` 为根目录；唯一约束 `(owner_id, parent_folder_id, filename)` |
| `ShareLink` | `ShareLink` | `verificationCode`、`shareFile`、`owner`、`downloadCount`、`downloadLimit`、`expireTime` | 分享链接；`downloadLimit=-1` 表示不限 |
| `SecurityQuestion` | `SecurityQuestion` | `question`、`answer`、`user` | 密保问题；答案以 BCrypt 保存 |
| `FileAnomaly` | `FileAnomaly` | `owner`、`fileId`、`logicalPath`、`anomalyType`、`description`、`resolved` | 启动一致性检查发现的异常快照 |

## 项目结构

```text
cloudstorage/
├── src/main/java/com/cloudstorage/
│   ├── config/          SecurityConfig、AppConfig、CacheConfig、DataInitializer
│   ├── controller/      Auth、File、ShareLink、ForgotPassword、Spa
│   ├── exception/       InvalidTokenException、TokenExpiredException
│   ├── model/
│   │   ├── dto/         Request / Response DTO
│   │   ├── entity/      User、UserFile、ShareLink、SecurityQuestion、FileAnomaly
│   │   └── enums/       AnomalyType
│   ├── repository/      Spring Data JPA 接口
│   ├── security/        AuthCookieService、JwtAuthenticationFilter
│   ├── service/         Auth、User、File、Storage、ShareLink、PasswordReset、MailClient、StartupConsistencyChecker
│   └── util/            JwtTokenUtil、FileUtils、RandomChar
├── src/main/resources/  application.properties、static/（前端构建产物）
├── src/test/java/       AuthCookieIntegrationTest 等
├── frontend/            React + Vite 源码
├── mail-service-go/     独立邮件微服务
├── docs/                API 文档、ADR、调研报告
├── docker-compose.yml   数据库 / Redis / 邮件服务 / 应用
├── Dockerfile           多阶段构建（前端 → 后端 → 运行时）
└── pom.xml
```

## 快速开始

### 环境要求

- JDK 17
- MariaDB 10.11+ / 11（默认 `localhost:3306`，库名 `cloudstorage`）
- Redis 7（密码找回功能需要；不启动时应用仍可运行，但找回接口会失败）
- Node.js 22（仅前端构建 / 开发）
- 可选：Go 1.26（运行 `mail-service-go`）或 Docker

### 本地运行

1. 创建数据库和用户（示例）：

```sql
CREATE DATABASE cloudstorage CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'cloudstorage'@'localhost' IDENTIFIED BY 'your-password';
GRANT ALL PRIVILEGES ON cloudstorage.* TO 'cloudstorage'@'localhost';
FLUSH PRIVILEGES;
```

2. 启动 Redis（任选其一）：

```bash
redis-server
# 或
docker run --rm -p 6379:6379 redis:7-alpine
```

3. 启动后端：

```bash
DB_PASSWORD=your-password ./mvnw spring-boot:run
```

默认连接 `jdbc:mariadb://localhost:3306/cloudstorage`，用户名 `cloudstorage`。如与本机配置不同，可设置：

```bash
DB_URL='jdbc:mariadb://localhost:3306/cloudstorage' \
DB_USERNAME='cloudstorage' \
DB_PASSWORD='your-password' \
REDIS_HOST='localhost' \
./mvnw spring-boot:run
```

启动后访问 `http://localhost:8080`。

### 前端开发

```bash
cd frontend
npm install
npm run dev
```

开发服务器运行在 `http://localhost:5173`，`/api` 会代理到 `http://localhost:8080` 并去掉 `/api` 前缀。

### 生产构建

```bash
cd frontend
npm install
npm run build          # 输出到 ../src/main/resources/static

cd ..
./mvnw package -DskipTests
java -jar target/cloudstorage-0.0.1-SNAPSHOT.jar
```

前端构建产物位于 `src/main/resources/static/`，按当前项目约定需要随源码提交。

### Docker Compose

`docker-compose.yml` 会启动 MariaDB、Redis、`mail-service-go` 和应用，并把数据持久化到 `${DATA_DIR:-/opt/cloudstorage/data}`。

```bash
# 在项目根目录准备 .env
cat > .env <<'EOF'
JWT_SECRET=请替换为至少32字节的随机字符串
MAIL_HOST=smtp.example.com
MAIL_PORT=587
MAIL_USERNAME=noreply@example.com
MAIL_PASSWORD=your-smtp-password
MAIL_FROM=noreply@example.com
MAIL_AUTH_TOKEN=请替换为邮件服务令牌
DATA_DIR=/opt/cloudstorage/data
EOF

docker compose up -d --build
```

访问 `http://localhost:8080`。代码变更后请保留 `--build`，否则 Compose 可能复用旧镜像。

> Compose 中的数据库账号（`clouduser` / `cloudpass`、root 密码 `rootpass`）是开发默认值，正式部署前请修改。

Compose 通过 `MAIL_SERVICE_URL=http://mail:8081/send` 把应用连接到邮件微服务。

### 测试账户

应用启动时由 `DataInitializer` 自动创建：

| 用户名 | 密码 | 角色 |
| --- | --- | --- |
| `admin` | `123456` | admin |
| `user` | `123456` | user |

> 这两个账户由应用启动时自动创建，生产环境务必修改密码或删除。

### 运行测试

```bash
DB_PASSWORD=your-password ./mvnw test
```

测试需要本地 MariaDB。Spring Boot 4.1 没有 `@AutoConfigureMockMvc`，集成测试通过 `MockMvcBuilders.webAppContextSetup(context)` 手动构建（见 `AuthCookieIntegrationTest`）。

## 配置项

常用环境变量：

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DB_URL` | `jdbc:mariadb://localhost:3306/cloudstorage` | 数据库 JDBC URL |
| `DB_DRIVER` | `org.mariadb.jdbc.Driver` | JDBC 驱动 |
| `DB_USERNAME` | `cloudstorage` | 数据库用户名 |
| `DB_PASSWORD` | 无（必填） | 数据库密码 |
| `REDIS_HOST` | `localhost` | Redis 地址 |
| `REDIS_PORT` | `6379` | Redis 端口 |
| `REDIS_PASSWORD` | 空 | Redis 密码 |
| `STORAGE_ROOT` | `./storage` | 用户文件根目录 |
| `JWT_SECRET` | 内置开发密钥 | JWT 签名密钥，生产环境必须替换 |
| `COOKIE_NAME` | `CS_TOKEN` | 认证 Cookie 名 |
| `COOKIE_SECURE` | `false` | 是否只允许 HTTPS 携带 Cookie；启用 TLS 后设为 `true` |
| `MAIL_SERVICE_URL` | `http://localhost:8081/send` | 邮件微服务地址 |
| `MAIL_AUTH_TOKEN` | `dev-token` | 调用邮件服务的 Bearer Token |
| `MAIL_RESET_SUBJECT` | `密码重置通知 - CloudStorage` | 重置邮件主题 |
| `APP_BASE_URL` | `http://localhost:5173` | 生成邮件重置链接的前端地址 |

`application.properties` 中的其他关键项：

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `cloudstorage.jwt.expiration-hours` | `2` | JWT / Cookie 有效期（小时） |
| `cloudstorage.password-reset.token-expiration-minutes` | `20` | Redis 重置 Token 有效期（分钟） |
| `spring.servlet.multipart.max-file-size` | `500MB` | 单文件上传上限 |
| `spring.servlet.multipart.max-request-size` | `500MB` | 单请求上传上限 |

以下配置键已预留但当前代码尚未使用：`cloudstorage.login.max-attempts`、`cloudstorage.login.lockout-minutes`、`cloudstorage.captcha.expiration-minutes`、`cloudstorage.share.default-expiration-hours`。

## 接口文档

完整的路径、请求体、响应体与状态码说明见 **[docs/api.md](docs/api.md)**。

开发环境通过 Vite 访问时，接口路径需要加 `/api` 前缀（代理会自动去掉）；生产环境与后端同源，直接使用 `/file/**`、`/share/**` 等路径。

## 尚未实现 / 已知限制

| 项目 | 状态 |
| --- | --- |
| 连续 5 次登录 / 注册失败后的 CAPTCHA | 配置键已预留，功能未实现 |
| 管理员后台（用户、分组、配额、安全策略、异常记录） | `/admin/**` 仅有授权规则，尚无接口与页面 |
| 在线文本编辑（保存确认、乐观锁、`.bak` 恢复） | 设计已定稿，尚未实现 |
| 回收站 / 软删除 | 尚未实现 |
| 密保问题设置入口 | 当前只有读取与校验接口，用户无法在界面设置密保 |
| 注册邮箱验证 | 注册后账户立即启用，未做邮件验证 |
| 分页 / 排序 | 目录列表与搜索均不分页，顺序未定义 |
| 文件夹分享 | 后端未禁止，但下载接口按文件流处理，实际分享文件更可靠 |

## 后续计划

- 登录 / 注册失败计数与 CAPTCHA
- 管理员界面与用户管理、配额管理、文件异常处理
- 在线文本编辑器（CodeMirror 6）
- 回收站与软删除
- 密保问题设置、邮箱验证
- 搜索、侧边栏、统一弹窗与 Tailwind 前端重构

## 相关文档

- [API 接口文档](docs/api.md)
- [ADR 0001：路径由文件树推导](docs/adr/0001-path-derived-from-tree.md)
- [ADR 0002：数据库与磁盘一致性协议](docs/adr/0002-db-disk-consistency-protocol.md)
- [ADR 0003：认证凭据迁移到 HttpOnly Cookie](docs/adr/0003-auth-cookie-migration.md)
- [在线编辑器选型调研](docs/research/online-editor-comparison.md)
- [邮件微服务说明](mail-service-go/README.md)
