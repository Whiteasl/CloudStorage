# CloudStorage API 接口文档

> 对应代码：`main` 分支（2026-10-26）
> 后端：Spring Boot 4.1.0，默认端口 `8080`
> 前端开发代理：`http://localhost:5173/api` → `http://localhost:8080`（代理会去掉 `/api` 前缀）

本文档描述后端实际暴露的 HTTP 接口。路径均相对于后端根地址；在 Vite 开发服务器下访问时，请在路径前加 `/api`。

---

## 1. 通用约定

### 1.1 路径前缀

| 环境 | 前端请求前缀 | 实际到达后端 |
| --- | --- | --- |
| 本地开发（Vite） | `/api/file/list` | `/file/list` |
| 生产（前后端一体） | `/file/list` | `/file/list` |

### 1.2 认证方式

接口使用 **HttpOnly Cookie** 认证，不读取 `Authorization` 请求头。

- Cookie 名：`CS_TOKEN`（可由 `COOKIE_NAME` 配置）
- 登录 / 注册成功后由服务端下发：

```http
Set-Cookie: CS_TOKEN=<JWT>; Max-Age=7200; Path=/; HttpOnly; SameSite=Strict
```

- 启用 HTTPS 后可通过 `COOKIE_SECURE=true` 追加 `Secure` 属性。
- JWT 有效期 2 小时（`cloudstorage.jwt.expiration-hours=2`），声明包含：

| 声明 | 含义 |
| --- | --- |
| `sub` | 用户名 |
| `id` | 用户 ID |
| `username` | 用户名 |
| `role` | `user` / `admin` |
| `type` | 固定为 `auth` |
| `iat` / `exp` | 签发时间 / 过期时间 |

- 浏览器会自动携带 Cookie。命令行、移动端等非浏览器客户端需要保存并回传 Cookie（例如 `curl -c cookie.txt` / `-b cookie.txt`）。
- 缺少 Cookie、签名错误或 JWT 过期时返回 `401`。
- 已登录但角色不足时返回 `403`。
- 若请求携带了名为 `CS_TOKEN` 但无效的 Cookie，过滤器会直接返回 `401`，即使目标端点本身是公开的；遇到这种情况可先清除 Cookie 再重试。

### 1.3 权限矩阵

| 路径 | 方法 | 访问要求 |
| --- | --- | --- |
| `/register`、`/login` | POST | 公开 |
| `/logout` | GET | 公开（清除 Cookie 并跳转） |
| `/me` | GET | 已登录 |
| `/forgot-password/**` | GET / POST | 公开 |
| `/security-questions`、`/verify-answers` | POST | 公开 |
| `/Share/**` | GET | 公开（注意首字母大写） |
| `/share/**` | 任意 | `USER` / `ADMIN` |
| `/file/**` | 任意 | `USER` / `ADMIN` |
| `/admin/**` | 任意 | `ADMIN`（预留，尚无接口） |
| `/reset-password/**` | 任意 | `USER` / `ADMIN`（预留，尚无接口） |
| `/`、`/login`、`/register`、`/files`、`/share`、`/share/*`、`/forgot-password/**` | GET | 前端 SPA 页面，公开 |
| `/css/**`、`/js/**`、`/assets/**`、`/index.html` 等 | GET | 静态资源，公开 |

> 注意 `/Share/**` 与 `/share/**` 是两个不同的路径：前者是公开的分享访问接口（大写 `S`），后者是登录用户的分享管理接口（小写 `s`）。
>
> 另需注意：`GET /share/*`（单段路径，包括 `/share/list`）会先命中前端 SPA 的 `permitAll` 规则，过滤器层不强制登录；但控制器需要当前用户上下文，客户端仍应始终携带 Cookie。

### 1.4 成功响应

- 大多数接口返回 JSON（`Content-Type: application/json`）。
- 部分接口只返回状态码和空响应体：

| 状态码 | 接口示例 |
| --- | --- |
| `200 OK` | 重命名、移动、删除分享、密码重置 |
| `201 Created` | 注册、上传、新建文件夹 |
| `204 No Content` | 删除文件 |
| `302 Found` | 注销 |
| `200 + 文件流` | 下载文件、访问分享 |

### 1.5 错误响应

错误分为两类。

**Spring Security 过滤器层错误**（未认证 / 越权）返回 JSON：

```json
{ "error": "无有效凭证" }
```

```json
{ "error": "权限不足" }
```

**业务层错误**由 `ResponseStatusException` 抛出，走 Spring Boot 默认 `/error` 处理。当前配置为 `server.error.include-message=never`，因此响应体通常只包含状态信息，不包含业务异常文本：

```json
{
  "timestamp": "2026-10-26T13:00:00.000+00:00",
  "status": 409,
  "error": "Conflict",
  "path": "/file/upload"
}
```

客户端应以 **HTTP 状态码** 为主要判断依据。如需在开发环境显示业务消息，可在 `application.properties` 中设置：

```properties
server.error.include-message=always
```

参数校验失败（`@Valid`，如用户名 / 密码为空）返回 `400`，同样走默认错误响应。

### 1.6 通用数据结构

#### FileResponse

文件 / 文件夹元数据。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | number | 文件 ID |
| `filename` | string | 文件名（不含路径） |
| `fileSize` | number | 字节数；文件夹为 `0` |
| `contentType` | string \| null | MIME 类型 |
| `isFolder` | boolean | 是否为文件夹。当前序列化还会额外输出一个 `folder` 字段（Lombok `isFolder()` getter 产生），客户端以 `isFolder` 为准 |
| `parentFolderId` | number | 父目录 ID；`0` 表示根目录 |
| `createdAt` | string | 创建时间，ISO-8601 |
| `updatedAt` | string | 修改时间，ISO-8601 |

示例：

```json
{
  "id": 12,
  "filename": "report.pdf",
  "fileSize": 20480,
  "contentType": "application/pdf",
  "isFolder": false,
  "folder": false,
  "parentFolderId": 0,
  "createdAt": "2026-10-26T12:00:00",
  "updatedAt": "2026-10-26T12:00:00"
}
```

#### ShareLinkResponse

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | number | 分享记录 ID |
| `verificationCode` | string | 分享码 |
| `filename` | string | 被分享文件名 |
| `fileSize` | number | 文件字节数 |
| `folder` | boolean | 是否为文件夹。注意当前 ShareLinkResponse 的 JSON 键是 `folder`，不是 `isFolder` |
| `ownerName` | string | 分享者用户名 |
| `downloadCount` | number | 已下载次数 |
| `downloadLimit` | number | 下载上限；`-1` 表示不限 |
| `createdAt` | string | 分享创建时间 |
| `expiredTime` | string | 过期时间 |

#### AuthResponse

```json
{ "username": "alice", "role": "user" }
```

#### CurrentUserResponse

```json
{ "id": 3, "username": "alice", "role": "user" }
```

#### ForgotPasswordResponse

```json
{ "success": true, "token": "<recover-jwt>" }
```

#### SecurityQuestionResponse

```json
{
  "questions": [
    { "id": 1, "question": "你的第一只宠物叫什么？" },
    { "id": 2, "question": "你的出生城市？" }
  ]
}
```

#### VerifySecurityAnswerResponse

```json
{ "judgment": true, "token": "<reset-token>" }
```

#### AnswerItem

```json
{ "id": 1, "answer": "旺财" }
```

### 1.7 时间、编码与单位

- 响应编码为 UTF-8。
- `LocalDateTime` 由 Jackson 序列化为不带时区的 ISO-8601 字符串，例如 `2026-10-26T13:00:00`。
- `fileSize` 单位为字节。
- `parentFolderId` / `folderId` / `targetFolderId` 省略、传 `null` 或传 `0` 均表示根目录；响应中根目录固定为 `0`，不会返回 `null`。
- `downloadLimit`：`-1` 表示不限次数；`0` 表示不允许下载；`-2` 及以下为非法值。

---

## 2. 快速开始（cURL）

以下示例假设后端运行在 `http://localhost:8080`。

```bash
# 1. 注册并保存 Cookie
curl -i -c cookie.txt \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"secret123","email":"alice@example.com"}' \
  http://localhost:8080/register

# 2. 查询当前用户
curl -s -b cookie.txt http://localhost:8080/me

# 3. 上传文件到根目录
curl -i -b cookie.txt \
  -F 'file=@./hello.txt' \
  http://localhost:8080/file/upload

# 4. 列出根目录
curl -s -b cookie.txt http://localhost:8080/file/list

# 5. 下载 fileId=1 的文件
curl -b cookie.txt -o hello.txt \
  'http://localhost:8080/file/download?fileId=1'

# 6. 创建分享：最多下载 5 次，24 小时后过期
curl -s -b cookie.txt \
  -H 'Content-Type: application/json' \
  -d '{"fileId":1,"downloadLimit":5,"expiredHours":24}' \
  http://localhost:8080/share/create
```

通过 Vite 开发服务器访问时，把 `http://localhost:8080` 换成 `http://localhost:5173/api`。

---

## 3. 认证接口

### 3.1 POST /register

注册用户，成功后自动登录并下发 Cookie。

- **认证**：公开
- **请求体**：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `username` | string | 是 | 用户名，`@NotBlank` |
| `password` | string | 是 | 明文密码，服务端 BCrypt 加密后存储 |
| `email` | string | 是 | 邮箱，`@NotBlank`；用于密码找回 |

```json
{
  "username": "alice",
  "password": "secret123",
  "email": "alice@example.com"
}
```

- **响应**：`201 Created`

```http
Set-Cookie: CS_TOKEN=<JWT>; Max-Age=7200; Path=/; HttpOnly; SameSite=Strict
Content-Type: application/json
```

```json
{ "username": "alice", "role": "user" }
```

- **错误**：

| 状态码 | 场景 |
| --- | --- |
| `400` | `username` / `password` / `email` 为空 |
| `409` | 用户名已存在，或邮箱已被注册 |

- **说明**：当前注册不校验邮箱格式、用户名 / 密码长度，也不发送验证邮件，账户创建后立即可用；注册用户角色固定为 `user`。

### 3.2 POST /login

用户名密码登录。

- **认证**：公开
- **请求体**：

```json
{ "username": "alice", "password": "secret123" }
```

- **响应**：`200 OK`，同时下发 `Set-Cookie`

```json
{ "username": "alice", "role": "user" }
```

- **错误**：

| 状态码 | 场景 |
| --- | --- |
| `400` | 用户名或密码为空 |
| `401` | 用户名 / 密码错误，或账户被禁用 |

- **说明**：当前未实现「连续失败 5 次后要求 CAPTCHA」；`cloudstorage.login.max-attempts` 等配置项尚未接入。

### 3.3 GET /me

查询当前登录用户。

- **认证**：需要 Cookie
- **响应**：`200 OK`

```json
{ "id": 3, "username": "alice", "role": "user" }
```

- **错误**：`401` 未携带、令牌无效或已过期。

- **说明**：响应体不包含 JWT，Token 只存在于 HttpOnly Cookie 中。

### 3.4 GET /logout

注销并清除认证 Cookie。

- **认证**：公开
- **响应**：`302 Found`

```http
Location: /login
Set-Cookie: CS_TOKEN=; Max-Age=0; Path=/; HttpOnly; SameSite=Strict
```

- **说明**：前端使用整页跳转 `window.location.href = BASE_URL + "/logout"` 触发注销。若携带的 Cookie 已损坏 / 过期，过滤器会先返回 `401`；正常情况下浏览器会在 `Max-Age` 到期后自动移除 Cookie。

---

## 4. 文件接口

所有 `/file/**` 接口都要求 `USER` 或 `ADMIN` 角色，并且只能操作当前登录用户自己的文件。文件不存在或不属于当前用户时统一返回 `404`，不泄露其他用户文件的存在性。

### 4.1 POST /file/upload

上传文件。

- **Content-Type**：`multipart/form-data`
- **表单字段**：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `file` | file | 是 | 上传的文件 |
| `parentFolderId` | number | 否 | 目标文件夹 ID；省略 / `0` 表示根目录 |

```bash
curl -b cookie.txt \
  -F 'file=@./hello.txt' \
  -F 'parentFolderId=5' \
  http://localhost:8080/file/upload
```

- **响应**：`201 Created`，空响应体。
- **错误**：

| 状态码 | 场景 |
| --- | --- |
| `400` | `parentFolderId` 指向的节点不是文件夹 |
| `404` | 用户或父目录不存在 |
| `409` | 配额不足；目标目录下已有同名文件 / 文件夹 |
| `500` | 磁盘写入、权限设置或数据库事务失败 |

- **说明**：
  - 单文件 / 单请求上限默认 500 MB。
  - 服务端先写临时文件 `.tmp---<uuid>.tmp`，再原子改名到最终路径，最后写入数据库并更新 `storageUsed`。
  - 上传完成后文件权限设置为 `rw-------`，移除执行位。
  - 同名判断同时受应用层检查和数据库唯一约束 `(owner_id, parent_folder_id, filename)` 保护。

### 4.2 GET /file/download

下载文件。

- **查询参数**：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `fileId` | number | 是 | 文件 ID |

- **响应**：`200 OK`，文件流。

```http
Content-Type: application/octet-stream
Content-Disposition: attachment; filename="hello.txt"
```

- **错误**：

| 状态码 | 场景 |
| --- | --- |
| `401` | 未登录 |
| `404` | 文件不存在或不属于当前用户 |
| `406` | 目标是文件夹（文件夹不允许直接下载） |
| `500` | 磁盘路径解析失败 |

- **说明**：文件夹需要先通过 `/file/compress` 打包成 ZIP 再下载。

### 4.3 POST /file/folder

新建文件夹。

- **请求体**：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `folderName` | string | 是 | 文件夹名 |
| `parentFolderId` | number | 否 | 父目录 ID；省略 / `0` 表示根目录 |

```json
{ "folderName": "documents", "parentFolderId": 0 }
```

- **响应**：`201 Created`，空响应体。
- **错误**：

| 状态码 | 场景 |
| --- | --- |
| `400` | 父节点不是文件夹 |
| `404` | 用户或父目录不存在 |
| `409` | 同目录下已有同名文件 / 文件夹；磁盘存在同名残留 |
| `500` | 目录创建或数据库写入失败 |

- **说明**：`folderName` 当前没有长度、非法字符等额外校验。

### 4.4 GET /file/list

列出指定目录下的文件与文件夹。

- **查询参数**：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `parentFolderId` | number | 否 | 目录 ID；省略 / `0` 表示根目录 |

- **响应**：`200 OK`，`FileResponse` 数组。

```json
[
  {
    "id": 2,
    "filename": "documents",
    "fileSize": 0,
    "contentType": null,
    "isFolder": true,
    "folder": true,
    "parentFolderId": 0,
    "createdAt": "2026-10-26T12:00:00",
    "updatedAt": "2026-10-26T12:00:00"
  },
  {
    "id": 3,
    "filename": "hello.txt",
    "fileSize": 12,
    "contentType": "text/plain",
    "isFolder": false,
    "folder": false,
    "parentFolderId": 0,
    "createdAt": "2026-10-26T12:01:00",
    "updatedAt": "2026-10-26T12:01:00"
  }
]
```

- **错误**：

| 状态码 | 场景 |
| --- | --- |
| `400` | 父节点不是文件夹 |
| `404` | 用户或父目录不存在 |

- **说明**：返回结果不排序、不分页，顺序由数据库决定；文件与文件夹混排，客户端通过 `isFolder` 区分。

### 4.5 DELETE /file/delete

删除文件或文件夹。

- **查询参数**：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `fileId` | number | 是 | 文件 / 文件夹 ID |

- **响应**：`204 No Content`，空响应体。
- **错误**：

| 状态码 | 场景 |
| --- | --- |
| `404` | 文件不存在或不属于当前用户 |
| `409` | 文件被其他数据引用，无法删除 |
| `500` | 数据库删除失败 |

- **说明**：
  - 删除文件夹会级联删除其全部子文件 / 子文件夹。
  - 相关的分享链接会一同删除。
  - 数据库删除成功后释放配额，再尽力删除磁盘内容；磁盘清理失败会记录到 `FileAnomaly`，接口仍返回成功。
  - 当前没有回收站，删除不可恢复。

### 4.6 PUT /file/rename

重命名文件或文件夹。

- **查询参数**：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `fileId` | number | 是 | 文件 ID |
| `newName` | string | 是 | 新名字，需要 URL 编码 |

```bash
curl -X PUT -b cookie.txt \
  'http://localhost:8080/file/rename?fileId=3&newName=hello2.txt'
```

- **响应**：`200 OK`，空响应体。
- **错误**：

| 状态码 | 场景 |
| --- | --- |
| `404` | 文件不存在或不属于当前用户 |
| `409` | 同目录下已有同名文件 / 文件夹 |
| `500` | 磁盘移动或数据库更新失败 |

- **说明**：重命名文件夹只修改文件夹自身一行，子文件通过 `parentFolderId` 继续引用，无需递归更新。

### 4.7 PUT /file/move

移动文件或文件夹到另一个目录。

- **查询参数**：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `fileId` | number | 是 | 源文件 / 文件夹 ID |
| `targetFolderId` | number | 否 | 目标目录 ID；省略 / `0` 表示根目录 |

- **响应**：`200 OK`，空响应体。
- **错误**：

| 状态码 | 场景 |
| --- | --- |
| `400` | 目标节点不是文件夹 |
| `404` | 源文件、目标目录或用户不存在 |
| `409` | 目标目录下已有同名项；把文件夹移动到自身或其后代目录 |
| `500` | 磁盘移动或数据库更新失败 |

### 4.8 GET /file/{fileId}

获取单个文件 / 文件夹的元数据。

- **路径参数**：`fileId`（number）
- **响应**：`200 OK`，`FileResponse`
- **错误**：`404` 文件不存在或不属于当前用户。

### 4.9 GET /file/search

按文件名搜索当前用户的全部文件。

- **查询参数**：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `keyword` | string | 是 | 搜索关键字 |

- **响应**：`200 OK`，`FileResponse` 数组。
- **错误**：

| 状态码 | 场景 |
| --- | --- |
| `400` | 缺少 `keyword` 参数 |
| `404` | 用户不存在 |

- **说明**：
  - 使用数据库 `LIKE %keyword%` 的包含匹配，跨全部目录。
  - 不分页、不排序；大小写敏感性取决于数据库排序规则（MariaDB 默认不区分大小写）。
  - `keyword` 为空字符串时可能返回当前用户的全部文件。

### 4.10 POST /file/batch-delete

批量删除。

- **请求体**：

```json
{ "ids": [3, 4, 5] }
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `ids` | number[] | 是 | 要删除的文件 / 文件夹 ID 集合 |

- **响应**：`200 OK`，返回删除失败的 ID 数组；全部成功时为 `[]`。

```json
[4]
```

- **说明**：
  - 每个 ID 独立执行删除，单个失败不会回滚其他删除，失败 ID 通过响应体返回。
  - 删除文件夹时同样级联删除子项与关联分享。
  - `ids` 为空集合时返回 `200` 且响应体为空。
  - `ids` 缺失或为 `null` 时可能返回 `500`，客户端应始终传入数组。

### 4.11 POST /file/compress

把选中的文件 / 文件夹压缩为 ZIP，并保存到当前网盘的指定目录。

- **请求体**：

```json
{
  "ids": [3, 4],
  "folderId": 0,
  "archiveName": "backup"
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `ids` | number[] | 是 | 要压缩的文件 / 文件夹 ID |
| `folderId` | number | 否 | ZIP 保存的目标目录；省略 / `0` 表示根目录 |
| `archiveName` | string | 否 | 压缩包基础名；为空时使用当前毫秒时间戳 |

- **响应**：`200 OK`，空响应体。
- **错误**：

| 状态码 | 场景 |
| --- | --- |
| `400` | 用户不存在；目标节点不是文件夹；目标目录下已有同名 ZIP |
| `404` | 目标目录不存在 |
| `409` | 有一个或多个源文件未找到；配额不足 |
| `500` | 压缩或磁盘写入失败 |

- **说明**：
  - 服务端会自动追加 `.zip` 后缀，`archiveName` 传 `backup` 生成 `backup.zip`；不要自带 `.zip`，否则会得到 `backup.zip.zip`。
  - 选中的文件夹会递归打包其子文件；重复选中的文件只打包一次。
  - 压缩前后各做一次配额检查。
  - 接口只负责生成 ZIP 并存入网盘，不直接返回文件流；客户端需要重新调用 `/file/list` 获取新文件，再通过 `/file/download` 下载。

---

## 5. 分享接口

### 5.1 POST /share/create

创建分享链接。

- **认证**：需要 Cookie
- **请求体**：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `fileId` | number | 是 | 被分享的文件 ID |
| `downloadLimit` | number | 是 | 下载次数上限；`-1` 表示不限，`0` 表示不允许下载 |
| `expiredHours` | number | 是 | 过期时间（小时，从创建时起算） |

```json
{
  "fileId": 3,
  "downloadLimit": 5,
  "expiredHours": 24
}
```

> `downloadLimit` 和 `expiredHours` 是 Java 基本类型 `int`。JSON 中省略时按 `0` 处理，而不是使用默认值；客户端必须显式传入。

- **响应**：`200 OK`，`ShareLinkResponse`

```json
{
  "id": 1,
  "verificationCode": "a1b2c3d4-Ab3dE9",
  "filename": "hello.txt",
  "fileSize": 12,
  "folder": false,
  "ownerName": "alice",
  "downloadCount": 0,
  "downloadLimit": 5,
  "createdAt": "2026-10-26T13:00:00",
  "expiredTime": "2026-10-27T13:00:00"
}
```

- **错误**：

| 状态码 | 场景 |
| --- | --- |
| `400` | `downloadLimit < -1` |
| `404` | 用户或文件不存在 |
| `409` | 该文件已存在分享链接（同一用户同一文件只允许一个分享） |

- **说明**：
  - 分享码格式为 UUID 前 8 位 + `-` + 6～8 位随机字符。
  - 重复分享时响应为 `409`；异常消息中包含已有分享码，但默认错误响应不显示业务消息，可通过 `/share/list` 查询。
  - 后端未禁止分享文件夹，但下载接口按文件流处理，建议只分享文件。

### 5.2 GET /share/list

列出当前用户创建的全部分享链接。

- **认证**：需要 Cookie
- **响应**：`200 OK`，`ShareLinkResponse` 数组。
- **错误**：`404` 用户不存在。
- **说明**：
  - 返回值包含已过期、已达到下载上限的分享，服务端不做过滤。
  - 安全匹配规则中 `GET /share/*` 先于 `/share/**` 命中 `permitAll`；未携带 Cookie 访问时请求会进入控制器但取不到用户上下文，因此客户端应始终携带 Cookie（不要依赖未登录访问）。

### 5.3 DELETE /share/delete

删除分享链接。

- **查询参数**：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `shareId` | number | 是 | 分享记录 ID |

- **响应**：`200 OK`，空响应体。
- **说明**：
  - 删除条件为 `shareId + 当前用户`；不存在或不属于当前用户时不做任何操作，仍返回 `200`。
  - 用户不存在时返回 `404`。

### 5.4 GET /Share/{code}/info

查询分享信息，公开访问。

- **路径参数**：`code`（分享码，区分大小写）
- **认证**：公开
- **响应**：`200 OK`，`ShareLinkResponse`
- **错误**：`404` 分享码不存在。
- **说明**：
  - 路径首字母必须大写：`/Share/...`，不是 `/share/...`。
  - 信息接口不检查是否过期、是否达到下载上限，仅返回元数据。

### 5.5 GET /Share/{code}

下载分享文件，公开访问。

- **路径参数**：`code`（分享码，区分大小写）
- **认证**：公开
- **响应**：`200 OK`，文件流

```http
Content-Type: application/octet-stream
Content-Disposition: attachment; filename="hello.txt"
```

- **错误**：

| 状态码 | 场景 |
| --- | --- |
| `403` | 下载次数已达到 `downloadLimit` |
| `404` | 分享码不存在 |
| `410` | 分享已过期 |

- **说明**：
  - 分享码本身即访问凭证，接口不要求额外的提取码或登录。
  - 通过过期与次数检查后会立即递增 `downloadCount`。
  - 分享文件的真实磁盘路径不会出现在响应中。

---

## 6. 密码找回接口

密码找回有两条路径，共用「找回流程 JWT」和「Redis 重置 Token」两种令牌：

```text
邮箱方式：
POST /forgot-password
  → POST /forgot-password/email
  → 邮件中的 /forgot-password/reset?token=<reset-token>
  → GET /forgot-password/validate?token=<reset-token>
  → POST /forgot-password/reset

密保方式：
POST /forgot-password
  → POST /security-questions
  → POST /verify-answers
  → 得到 <reset-token>
  → POST /forgot-password/reset
```

| 令牌 | 形式 | 有效期 | 用途 |
| --- | --- | --- | --- |
| 找回流程 JWT | JWT，`type=recover`，含 `email` | 20 分钟 | 获取密保问题、提交密保答案 |
| 重置 Token | UUID，Redis 键 `reset:token:<token>` | 20 分钟（`cloudstorage.password-reset.token-expiration-minutes`） | 校验重置链接、执行密码重置 |

> Redis 不可用时，签发 / 校验 / 删除重置 Token 的接口会返回 `500`。

### 6.1 POST /forgot-password

校验邮箱是否存在，并签发找回流程 JWT。

- **认证**：公开
- **请求体**：

```json
{ "email": "alice@example.com" }
```

- **响应**：`200 OK`

```json
{ "success": true, "token": "<recover-jwt>" }
```

- **错误**：

| 状态码 | 场景 |
| --- | --- |
| `400` | `email` 为空 |
| `404` | 邮箱未注册 |

- **说明**：该接口只签发找回流程 JWT，不发送邮件；邮件由 `/forgot-password/email` 发送。

### 6.2 POST /forgot-password/email

发送密码重置邮件。

- **认证**：公开
- **请求体**：

```json
{ "email": "alice@example.com" }
```

- **响应**：`200 OK`，空响应体。
- **错误**：`404` 邮箱未注册；`400` 邮箱为空。
- **说明**：
  - 邮件正文中的链接为 `${APP_BASE_URL}/forgot-password/reset?token=<reset-token>`。
  - 生成的重置 Token 存入 Redis，有效期默认 20 分钟，重置成功后删除。
  - 邮件服务不可达时，`MailClient` 会捕获异常并把重置链接输出到后端日志，接口仍返回 `200`。

### 6.3 POST /security-questions

使用找回流程 JWT 获取当前用户的密保问题。

- **认证**：公开
- **请求体**：

```json
{ "token": "<recover-jwt>" }
```

- **响应**：`200 OK`

```json
{
  "questions": [
    { "id": 1, "question": "你的第一只宠物叫什么？" },
    { "id": 2, "question": "你的出生城市？" }
  ]
}
```

- **错误**：

| 状态码 | 场景 |
| --- | --- |
| `400` | 账户异常 |
| `410` | 用户尚未设置密保问题 |
| `500` | 找回流程 JWT 无效或已过期（当前未映射为 4xx） |

### 6.4 POST /verify-answers

校验密保答案。

- **认证**：公开
- **请求体**：

```json
{
  "token": "<recover-jwt>",
  "answers": [
    { "id": 1, "answer": "旺财" },
    { "id": 2, "answer": "北京" }
  ]
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `token` | string | 是 | 找回流程 JWT |
| `answers` | AnswerItem[] | 是 | 至少一条，元素包含 `id` 与 `answer` |

- **响应**：`200 OK`

答对比例 ≥ 75% 时：

```json
{ "judgment": true, "token": "<reset-token>" }
```

未通过时：

```json
{ "judgment": false, "token": "" }
```

- **错误**：

| 状态码 | 场景 |
| --- | --- |
| `400` | 找回流程 JWT 对应的账户异常；`answers` 为空 |
| `500` | 找回流程 JWT 无效或已过期（当前未映射为 4xx） |

- **说明**：通过后返回的 `<reset-token>` 是 Redis 中的一次性重置令牌，用于 `/forgot-password/reset`。

### 6.5 GET /forgot-password/validate

校验重置 Token 是否仍然有效。

- **认证**：公开
- **查询参数**：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `token` | string | 是 | Redis 重置 Token |

- **响应**：`200 OK`，布尔值

```json
true
```

- **说明**：
  - Token 为空字符串、Redis 中不存在时返回 `false`；完全不传 `token` 参数时 Spring 返回 `400`。
  - 校验不会删除 Token，重置成功后才会删除。
  - Redis 不可用时返回 `500`。

### 6.6 POST /forgot-password/reset

使用重置 Token 设置新密码。

- **认证**：公开
- **请求体**：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `token` | string | 是 | Redis 重置 Token |
| `newPassword` | string | 是 | 新密码（明文，服务端 BCrypt 加密） |

```json
{ "token": "<reset-token>", "newPassword": "newSecret123" }
```

- **响应**：`200 OK`，空响应体。
- **错误**：

| 状态码 | 场景 |
| --- | --- |
| `400` | Token 无效 / 已过期；账户异常；字段为空 |

- **说明**：
  - 重置成功后更新密码、发送通知邮件，并删除 Redis 中的重置 Token。
  - 当前没有密码强度校验。

---

## 7. 预留与其他端点

| 路径 | 说明 |
| --- | --- |
| `/admin/**` | 仅 `ADMIN` 可访问；尚未实现控制器。管理员访问得到 `404`，普通用户得到 `403`，未登录得到 `401` |
| `/reset-password/**` | 仅 `USER` / `ADMIN` 可访问；尚未实现控制器 |
| `/error` | Spring Boot 默认错误端点，公开 |
| `/`、`/login`、`/register`、`/files`、`/share`、`/share/{code}`、`/forgot-password`、`/forgot-password/reset` | `SpaController` 转发到 `index.html`，返回前端页面 |

---

## 8. 状态码汇总

| 状态码 | 含义 | 常见场景 |
| --- | --- | --- |
| `200` | 成功 | 查询、登录、重命名、移动、分享创建 / 删除、密码重置 |
| `201` | 已创建 | 注册、上传、新建文件夹 |
| `204` | 无内容 | 删除文件 |
| `302` | 重定向 | 注销后跳转 `/login` |
| `400` | 请求错误 | 参数校验失败、父节点不是文件夹、非法下载次数、Token 无效 |
| `401` | 未认证 | 未携带 Cookie、JWT 无效或过期、登录凭据错误 |
| `403` | 无权限 | 角色不足、分享下载次数达到上限 |
| `404` | 未找到 | 用户 / 文件 / 父目录 / 分享码不存在 |
| `406` | 不可接受 | 尝试直接下载文件夹 |
| `409` | 冲突 | 同名文件 / 文件夹、配额不足、目录环、文件已分享、文件被引用 |
| `410` | 已失效 | 分享链接过期、未设置密保问题 |
| `500` | 服务器错误 | 磁盘 IO、数据库事务、压缩失败、未映射的令牌异常 |

---

## 9. 已知行为与注意事项

1. **错误消息默认不可见**：`server.error.include-message=never`，业务异常的具体中文消息不会出现在响应体中，请以状态码为准；开发环境可临时改为 `always`。
2. **未实现 CAPTCHA / 登录锁定**：连续失败 5 次后要求验证码的需求尚未实现，`cloudstorage.login.max-attempts` 等配置目前不生效。
3. **未实现分页与排序**：`/file/list`、`/file/search`、`/share/list` 都返回全量结果，顺序未定义；文件较多时客户端需自行处理。
4. **分享码即凭证**：`/Share/{code}` 不需要登录或二次提取码，拿到链接的人即可下载；分享码应通过可信渠道传递。
5. **文件夹分享**：创建分享时后端不校验 `isFolder`，但下载接口按文件流打开目录，实际可能失败；建议只分享文件。
6. **批量删除非原子**：`/file/batch-delete` 对每个 ID 独立删除，部分失败时通过返回数组告知，其他文件已经删除且不会回滚。
7. **压缩不返回流**：`/file/compress` 只把 ZIP 写入网盘，需要再调用列表和下载接口。
8. **省略整数参数会按 0 处理**：`downloadLimit`、`expiredHours` 是 `int`，JSON 中省略时不会使用服务端默认值。
9. **找回流程 JWT 异常**：`/security-questions`、`/verify-answers` 中无效或过期的找回 JWT 抛出的是自定义运行时异常，未映射为 `401` / `400`，当前返回 `500`。
10. **Cookie 与 CSRF**：认证依赖 `HttpOnly + SameSite=Strict` Cookie，不使用 CSRF Token；跨站请求不会携带 Cookie。若客户端不是浏览器，需要自行管理 Cookie。
11. **时间字段无时区**：`LocalDateTime` 序列化为不带时区的 ISO-8601 字符串，客户端不要假设其为 UTC。
