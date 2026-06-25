# API 接口文档

> RESTful API 和 WebSocket 接口规范

---

## 架构分离说明

本系统采用**云端+本地混合架构**，支持三种启动模式：

### 三模式启动策略

| 模式 | 用途 | 数据存储 | 启动方式 | 适用场景 |
|------|------|---------|---------|---------|
| **LOCAL（本地）** | AI隐私数据管理 | H2本地数据库 | 内嵌后端，精简启动 | 纯AI聊天、隐私模式、云端不可用 |
| **HOTSPOT（热点）** | 开发测试 | MySQL+Redis | 连接局域网服务器 | 功能测试、多人协作验证 |
| **CLOUD（云端）** | 真人实时通信 | MySQL+Redis | 连接公网服务器 | 真人聊天、群聊（含AI）、多人协作 |

#### 启动流程

```
用户双击应用 → 检查 SERVER_MODE 环境变量
                ↓
        ┌───────┴───────┐
        │               │
    LOCAL            HOTSPOT/CLOUD
        │               │
  启动内嵌后端      跳过内嵌后端
        │               │
  H2数据库         连接远程服务器
        │               │
        └───────┬───────┘
                ↓
        异步检查云端服务器
                ↓
        云端可用 → 覆盖本地连接（真人聊天）
        云端不可用 → 仅使用本地模式
                ↓
        隐私模式开启 → 强制本地模式
```

#### 环境变量配置

| 变量 | 说明 | 示例 |
|------|------|------|
| `SERVER_MODE` | 启动模式选择 | `local` / `hotspot` / `cloud` |
| `CLOUD_SERVER_URL` | 云端服务器地址 | `https://your-cloud-server.com/api` |
| `HOTSPOT_SERVER_URL` | 热点服务器地址 | `http://192.168.1.100:8080/api` |
| `PRIVACY_MODE` | 隐私模式开关 | `true` / `false` |

#### 群聊AI网络需求判断

| 场景 | 是否需要云端 | 说明 |
|------|-------------|------|
| 纯AI聊天（不含群主） | ❌ 不需要 | 可以使用本地模式 |
| 群聊包含真人成员 | ✅ 需要 | 真人消息需要云端转发 |
| 群聊包含AI | ✅ 需要 | AI回复需云端广播给其他成员 |
| 隐私模式开启 | ❌ 强制本地 | 用户主动选择隐私保护 |

#### 配置文件说明

| 配置文件 | 用途 | 特点 |
|---------|------|------|
| `application-local.yml` | 本地模式 | H2数据库，仅AI组件，精简启动（~3秒） |
| `application-hotspot.yml` | 热点模式 | MySQL+Redis，完整功能，局域网访问 |
| `application-cloud.yml` | 云端模式 | MySQL+Redis，HTTPS，CORS配置 |

---

### 云端API（公网服务器）

**用途**：真人实时通信、多人协作、在线状态管理

**数据特点**：需要多人共享、实时同步、持久化存储

**Base URL**: `https://your-cloud-server.com/api`（或公网IP）

**模块归属**：
- 认证模块（`/auth/*`）：用户登录、注册、验证码
- 用户模块（`/user/*`）：个人信息、搜索用户
- 好友模块（`/friend/*`）：好友申请、好友列表、在线状态
- 群组模块（`/group/*`）：创建群、群成员管理、群信息
- 消息模块（真人）：`/message/*`（真人消息发送、撤回、已读）
- 会话模块（`/conversation/*`）：真人会话列表
- 图片上传（`/message/upload/image`）：真人聊天图片存储
- WebSocket（真人消息）：`SEND_MESSAGE`、`RECEIVE_MESSAGE`、`GROUP_MESSAGE`

### 本地API（内嵌服务器）

**用途**：AI隐私数据管理、本地向量检索、离线AI聊天

**数据特点**：敏感数据、不应离开用户设备、本地H2存储

**Base URL**: `http://localhost:8080/api`（内嵌服务）

**模块归属**：
- AI模块（`/ai/*`）：AI角色管理、AI对话、AI记忆、AI群配置
- 本地健康检查（`/local/health`）：本地服务状态
- 本地配置管理（`/local/config`）：本地服务配置
- 向量检索（`/local/vector/*`）：本地向量存储与检索（补充API）
- WebSocket（AI消息）：`AI_CHAT`、`AI_STREAM`

### 混合场景说明

| 场景 | 云端API | 本地API | 说明 |
|------|---------|---------|------|
| 用户登录 | `/auth/login` | - | 认证走云端 |
| AI角色创建 | - | `/ai/create` | AI配置本地存储 |
| 真人聊天 | `/message/send` | - | 真人消息云端转发 |
| AI聊天 | - | WebSocket `AI_CHAT` | AI对话本地处理 |
| 群聊（真人） | WebSocket `GROUP_MESSAGE` | - | 真人消息云端广播 |
| 群聊（AI） | WebSocket `GROUP_MESSAGE`（真人部分） | WebSocket `AI_CHAT`（AI部分） | 双通道协作 |
| 会话列表 | `/conversation/list`（真人会话） | `/ai/conversations`（AI会话） | 客户端合并展示 |

---

## 基础信息

### 云端服务器

**Base URL**: `https://your-cloud-server.com/api`

**认证方式**: JWT Token（云端颁发，本地验证）

**请求头**:
```
Authorization: Bearer <token>
Content-Type: application/json
```

### 本地服务器

**Base URL**: `http://localhost:8080/api`

**认证方式**: JWT Token（云端颁发，本地公钥验签）

**请求头**:
```
Authorization: Bearer <token>
Content-Type: application/json
```

**响应格式**:
```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

**分页请求参数**（所有分页接口统一）:
| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| page | int | 1 | 页码（从1开始） |
| size | int | 20 | 每页数量（最大100） |

**分页响应格式**（所有分页接口统一）:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [],
    "total": 100,
    "page": 1,
    "size": 20
  }
}
```

**会话ID生成规则**:
| 场景 | session_id 格式 | 示例 |
|------|----------------|------|
| 单聊 | `p_{min(userId1,userId2)}_{max(userId1,userId2)}` | `p_1001_1002` |
| 群聊 | `g_{groupId}` | `g_2001` |
| AI单聊 | `a_{aiId}_{userId}` | `a_3001_1001` |

**错误码**:
| 错误码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未认证 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 500 | 服务器错误 |

**业务错误码**:
| 错误码 | 模块 | 说明 |
|--------|------|------|
| 1001 | 认证 | 手机号已注册 |
| 1002 | 认证 | 验证码错误或已过期 |
| 1003 | 认证 | 用户名已存在 |
| 1004 | 认证 | 账号或密码错误 |
| 1005 | 认证 | 登录次数过多，账号已锁定 |
| 1006 | 认证 | Refresh Token 已失效 |
| 2001 | 好友 | 好友申请已存在（待处理） |
| 2002 | 好友 | 已是好友关系 |
| 2003 | 好友 | 不能添加自己为好友 |
| 3001 | 群组 | 无权执行此操作 |
| 3002 | 群组 | 群成员已满 |
| 3003 | 群组 | 已在群中 |
| 4001 | 消息 | 消息已超过2分钟，不可撤回 |
| 4002 | 消息 | 无权撤回他人消息 |
| 4003 | 消息 | 图片格式不支持 |
| 4004 | 消息 | 图片大小超出限制 |
| 5001 | AI | API Key 无效 |
| 5002 | AI | AI 不存在或已禁用 |

---

## 一、认证模块

### 1.1 发送验证码
```
POST /auth/sms/send
```

**请求参数**:
```json
{
  "phone": "13800138000"
}
```

**响应**:
```json
{
  "code": 200,
  "message": "验证码已发送",
  "data": null
}
```

### 1.2 用户注册
```
POST /auth/register
```

**说明**: 注册成功后自动登录，返回完整登录态（与登录接口响应结构一致）。

**请求参数**:
```json
{
  "phone": "13800138000",
  "code": "123456",
  "username": "张三",
  "password": "password123"
}
```

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "expiresIn": 7200,
    "user": {
      "userId": 1001,
      "phone": "138****8000",
      "username": "张三",
      "avatar": "http://minio.example.com/avatar/001.jpg"
    }
  }
}
```

### 1.3 用户登录
```
POST /auth/login
```

**请求参数**:
```json
{
  "phone": "13800138000",
  "password": "password123",
  "rememberMe": true
}
```

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "expiresIn": 7200,
    "user": {
      "userId": 1001,
      "phone": "138****8000",
      "username": "张三",
      "avatar": "http://minio.example.com/avatar/001.jpg"
    }
  }
}
```

### 1.4 刷新Token
```
POST /auth/refresh
```

**说明**: Refresh Token 一次性有效，使用后自动失效。客户端收到新的 access token 后，需同时保存服务端返回的新 refresh token。

**请求参数**:
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

**响应**:
```json
{
  "code": 200,
  "message": "刷新成功",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "expiresIn": 7200
  }
}
```

### 1.5 忘记密码
```
POST /auth/forgot-password
```

**说明**: 通过手机号+短信验证码重置密码，无需登录态。

**请求参数**:
```json
{
  "phone": "13800138000",
  "code": "123456",
  "newPassword": "newpassword123",
  "confirmPassword": "newpassword123"
}
```

**响应**:
```json
{
  "code": 200,
  "message": "密码重置成功",
  "data": null
}
```

---

## 二、用户模块

### 2.1 获取个人信息
```
GET /user/profile
```

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "userId": 1001,
    "phone": "138****8000",
    "username": "张三",
    "avatar": "http://minio.example.com/avatar/001.jpg",
    "bio": "这个人很懒，什么都没写",
    "gender": 0,
    "age": null,
    "birthday": null,
    "detailBio": null,
    "createTime": "2024-01-01T00:00:00Z"
  }
}
```

**字段说明**:
| 字段 | 类型 | 说明 |
|------|------|------|
| userId | Long | 用户ID |
| phone | String | 手机号（脱敏） |
| username | String | 用户名 |
| avatar | String | 头像URL |
| bio | String | 个人简介 |
| gender | Integer | 性别：0-未知，1-男，2-女 |
| age | Integer | 年龄 |
| birthday | String | 生日（yyyy-MM-dd） |
| detailBio | String | 个人详细说明 |
| createTime | String | 注册时间 |
```

### 2.2 修改个人信息
```
PUT /user/profile
```

**请求参数**（所有字段可选，仅传需要修改的字段）:
```json
{
  "username": "张三三",
  "avatar": "http://minio.example.com/avatar/002.jpg",
  "bio": "我是一个程序员",
  "gender": 1,
  "age": 25,
  "birthday": "1999-01-01",
  "detailBio": "热爱编程和音乐"
}
```

**字段说明**:
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | String | 否 | 用户名（2-50字符） |
| avatar | String | 否 | 头像URL（最长500字符） |
| bio | String | 否 | 个人简介（最长500字符） |
| gender | Integer | 否 | 性别：0-未知，1-男，2-女 |
| age | Integer | 否 | 年龄（0-200） |
| birthday | String | 否 | 生日（yyyy-MM-dd） |
| detailBio | String | 否 | 个人详细说明（最长2000字符） |
```

**响应**:
```json
{
  "code": 200,
  "message": "修改成功",
  "data": null
}
```

### 2.3 搜索用户
```
GET /user/search?keyword=张三&page=1&size=20
```

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "userId": "user_001",
        "username": "张三",
        "avatar": "http://minio.example.com/avatar/001.jpg",
        "bio": "程序员"
      }
    ],
    "total": 5,
    "page": 1,
    "size": 20
  }
}
```

### 2.4 修改密码
```
PUT /user/password
```

**说明**: 需验证当前手机号短信验证码。

**请求参数**:
```json
{
  "smsCode": "123456",
  "newPassword": "newpassword123",
  "confirmPassword": "newpassword123"
}
```

**响应**:
```json
{
  "code": 200,
  "message": "密码修改成功",
  "data": null
}
```

### 2.5 修改手机号
```
PUT /user/phone
```

**说明**: 需同时验证旧手机号和新手机号的短信验证码。

**请求参数**:
```json
{
  "smsCode": "123456",
  "newPhone": "13900139000",
  "newSmsCode": "654321"
}
```

**响应**:
```json
{
  "code": 200,
  "message": "手机号修改成功",
  "data": null
}
```

---

## 三、好友模块

### 3.1 发送好友申请
```
POST /friend/apply
```

**说明**: 通过手机号查找目标用户。如果已存在待处理的申请，返回错误码 `2001`。如果对方已发送过申请，自动接受并建立好友关系。

**请求参数**:
```json
{
  "targetPhone": "13900139000",
  "message": "我是张三，加个好友"
}
```

**响应**:
```json
{
  "code": 200,
  "message": "申请已发送",
  "data": null
}
```

### 3.2 获取好友申请列表
```
GET /friend/apply/list
```

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "applyId": "apply_001",
        "userId": "user_003",
        "username": "李四",
        "avatar": "http://minio.example.com/avatar/003.jpg",
        "message": "你好，认识一下",
        "status": "PENDING",
        "createTime": "2024-01-01T10:00:00Z"
      }
    ]
  }
}
```

### 3.3 处理好友申请
```
POST /friend/apply/{applyId}/handle
```

**请求参数**:
```json
{
  "action": "ACCEPT"
}
```

**响应**:
```json
{
  "code": 200,
  "message": "已同意",
  "data": null
}
```

### 3.4 获取好友列表
```
GET /friend/list
```

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "userId": "user_002",
        "username": "李四",
        "avatar": "http://minio.example.com/avatar/003.jpg",
        "bio": "设计师",
        "remark": "四哥",
        "online": true
      }
    ]
  }
}
```

### 3.5 删除好友
```
DELETE /friend/{friendId}
```

**响应**:
```json
{
  "code": 200,
  "message": "已删除",
  "data": null
}
```

---

## 四、消息模块

### 消息收发通道说明

消息发送支持两种通道，适用场景不同：

| 通道 | 适用场景 | 特点 |
|------|---------|------|
| WebSocket `SEND_MESSAGE` | 实时聊天（主要通道） | 低延迟，服务端直接转发 |
| REST `POST /message/send` | 离线补发、历史消息重试、机器人发送 | 可靠性高，支持重试 |

**客户端默认使用 WebSocket 发送**，仅在 WebSocket 断连或需要可靠投递时使用 REST 接口。

### 4.1 获取会话列表
```
GET /conversation/list?page=1&size=20&keyword=
```

**说明**: 获取当前用户所有会话，按最后一条消息时间倒序排列。客户端首页使用此接口。`keyword` 参数可选，用于按会话名称模糊搜索过滤。

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "sessionId": "p_1001_1002",
        "targetId": "user_002",
        "targetType": "USER",
        "targetName": "李四",
        "targetAvatar": "http://minio.example.com/avatar/003.jpg",
        "lastMessage": "你好",
        "lastMessageType": "TEXT",
        "lastMessageTime": "2024-01-01T10:00:00Z",
        "unreadCount": 3
      },
      {
        "sessionId": "g_2001",
        "targetId": "group_001",
        "targetType": "GROUP",
        "targetName": "技术交流群",
        "targetAvatar": "http://minio.example.com/group/001.jpg",
        "lastMessage": "大家好",
        "lastMessageType": "TEXT",
        "lastMessageTime": "2024-01-01T09:30:00Z",
        "unreadCount": 10
      }
    ],
    "total": 15,
    "page": 1,
    "size": 20
  }
}
```

### 4.2 获取聊天记录
```
GET /message/history?sessionId=p_1001_1002&page=1&size=20
```

**说明**: 按会话维度拉取消息，支持分页。加载更多历史消息时使用上一次返回的最小 `createTime` 作为游标。

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "messageId": "msg_001",
        "sessionId": "p_1001_1002",
        "senderId": "user_001",
        "senderName": "张三",
        "senderAvatar": "http://minio.example.com/avatar/001.jpg",
        "senderType": "USER",
        "type": "TEXT",
        "content": "你好",
        "createTime": "2024-01-01T10:00:00Z",
        "recalled": false
      }
    ],
    "total": 100,
    "page": 1,
    "size": 20
  }
}
```

### 4.3 发送消息（REST，备用通道）
```
POST /message/send
```

**说明**: REST 发送消息的备用通道，用于离线补发或重试。实时聊天请使用 WebSocket `SEND_MESSAGE`。

**请求参数**:
```json
{
  "sessionId": "p_1001_1002",
  "type": "TEXT",
  "content": "你好"
}
```

**响应**:
```json
{
  "code": 200,
  "message": "发送成功",
  "data": {
    "messageId": "msg_001",
    "createTime": "2024-01-01T10:00:00Z"
  }
}
```

### 4.4 撤回消息
```
POST /message/recall
```

**说明**:
- 人-人消息：发送后 2 分钟内可撤回，超时返回错误码 `4001`
- 人-AI 消息：可随时撤回
- 群消息撤回：仅群主和管理员可撤回他人消息，普通成员只能撤回自己的消息，否则返回错误码 `4002`
- 撤回成功后，服务端会通过 WebSocket 推送 `MESSAGE_RECALL` 通知给对方（私聊）或群成员（群聊），前端据此更新消息显示为"已撤回"

**请求参数**:
```json
{
  "messageId": "100"
}
```

**响应**:
```json
{
  "code": 200,
  "message": "已撤回",
  "data": {
    "messageId": 100,
    "sessionId": "p_1001_1002",
    "senderId": 1001
  }
}
```

### 4.5 消息已读回执
```
POST /message/read
```

**说明**: 客户端打开会话时批量上报已读消息。上报后服务端会通过 WebSocket 推送 `READ_RECEIPT` 通知给消息发送者。

**请求参数**:
```json
{
  "sessionId": "p_1001_1002",
  "messageIds": [1, 2, 3]
}
```

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

### 4.6 上传图片
```
POST /message/upload/image
Content-Type: multipart/form-data
```

**限制**:
- 支持格式：JPEG、PNG、GIF、WebP
- 最大大小：10MB
- 服务端自动压缩（最大宽度 1080px）并生成缩略图

**请求参数**:
```
file: [图片文件]
```

**响应**:
```json
{
  "code": 200,
  "message": "上传成功",
  "data": {
    "fileId": "file_001",
    "url": "http://minio.example.com/chat/image/001.jpg",
    "thumbnailUrl": "http://minio.example.com/chat/image/001_thumb.jpg",
    "width": 1920,
    "height": 1080,
    "size": 1024000,
    "fileType": "image/jpeg"
  }
}
```

---

## 五、群组模块

### 5.1 创建群组
```
POST /group/create
```

**请求参数**:
```json
{
  "name": "技术交流群",
  "memberIds": [1002, 1003, 1004]
}
```

**字段说明**:
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | String | 是 | 群组名称（最长50字符） |
| memberIds | Long[] | 否 | 初始成员ID列表。可为空或省略，此时仅创建者自己入群，后续可通过邀请接口添加成员 |

**响应**:
```json
{
  "code": 200,
  "message": "创建成功",
  "data": {
    "groupId": "group_001",
    "name": "技术交流群"
  }
}
```

### 5.2 获取群列表
```
GET /group/list?page=1&size=20
```

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "groupId": "group_001",
        "name": "技术交流群",
        "avatar": "http://minio.example.com/group/001.jpg",
        "memberCount": 50,
        "ownerId": "user_001"
      }
    ],
    "total": 5,
    "page": 1,
    "size": 20
  }
}
```

### 5.3 获取群成员
```
GET /group/{groupId}/members?page=1&size=50
```

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "userId": "user_001",
        "username": "张三",
        "avatar": "http://minio.example.com/avatar/001.jpg",
        "role": "OWNER",
        "joinTime": "2024-01-01T00:00:00Z"
      }
    ],
    "total": 50,
    "page": 1,
    "size": 50
  }
}
```

### 5.4 修改群信息
```
PUT /group/{groupId}
```

**说明**: 仅群主可修改。

**请求参数**:
```json
{
  "name": "新群名",
  "announcement": "群公告内容",
  "announcementPinned": true
}
```

**响应**:
```json
{
  "code": 200,
  "message": "修改成功",
  "data": null
}
```

### 5.5 邀请成员
```
POST /group/{groupId}/invite
```

**说明**: 群主、管理员、普通成员均可邀请（受群最大人数限制）。

**请求参数**:
```json
{
  "userIds": ["user_005", "user_006"]
}
```

**响应**:
```json
{
  "code": 200,
  "message": "邀请成功",
  "data": null
}
```

### 5.6 移除成员
```
DELETE /group/{groupId}/members/{userId}
```

**说明**: 仅群主和管理员可操作，群主不可被移除。

**响应**:
```json
{
  "code": 200,
  "message": "已移除",
  "data": null
}
```

### 5.7 退出群组
```
POST /group/{groupId}/leave
```

**说明**: 群主不可退出，需先转让群主或解散群组。

**响应**:
```json
{
  "code": 200,
  "message": "已退出",
  "data": null
}
```

---

## 六、AI模块

### 6.1 获取AI列表
```
GET /ai/list?page=1&size=20
```

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "aiId": "ai_001",
        "name": "小助手",
        "avatar": "http://minio.example.com/ai/001.jpg",
        "persona": "你是一个友好的助手",
        "modelProvider": "openai",
        "model": "gpt-4",
        "isGroup": false
      }
    ],
    "total": 3,
    "page": 1,
    "size": 20
  }
}
```

### 6.2 创建AI角色
```
POST /ai/create
```

**请求参数**:
```json
{
  "name": "小助手",
  "persona": "你是一个友好的助手，喜欢帮助别人",
  "modelProvider": "openai",
  "model": "gpt-4",
  "apiKey": "sk-xxx",
  "isGroup": false
}
```

**说明**: `apiKey` 服务端使用 AES-256-GCM 加密后存储，不保存明文。`isGroup` 为 true 表示该 AI 可用于群聊。

**响应**:
```json
{
  "code": 200,
  "message": "创建成功",
  "data": {
    "aiId": "ai_001"
  }
}
```

### 6.3 修改AI角色
```
PUT /ai/{aiId}
```

**请求参数**:
```json
{
  "name": "小助手Pro",
  "persona": "你是一个专业的技术助手",
  "model": "gpt-4-turbo"
}
```

**响应**:
```json
{
  "code": 200,
  "message": "修改成功",
  "data": null
}
```

### 6.4 删除AI角色
```
DELETE /ai/{aiId}
```

**响应**:
```json
{
  "code": 200,
  "message": "已删除",
  "data": null
}
```

### 6.5 AI对话（REST，同步模式）
```
POST /ai/chat
```

**说明**: REST 方式等待 AI 完整响应后返回，适用于非实时场景（如后台任务、消息补发）。实时聊天请使用 WebSocket `AI_CHAT` + `AI_STREAM`。

**请求参数**:
```json
{
  "aiId": "ai_001",
  "content": "你好",
  "conversationId": "conv_001"
}
```

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "conversationId": "conv_001",
    "content": "你好！有什么我可以帮助你的吗？",
    "messageId": "msg_ai_001"
  }
}
```

### 6.6 获取AI记忆
```
GET /ai/{aiId}/memories?userId=user_001&page=1&size=10
```

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "memoryId": "mem_001",
        "summary": "用户喜欢编程和音乐",
        "keywords": ["编程", "音乐"],
        "createTime": "2024-01-01T00:00:00Z"
      }
    ],
    "total": 8,
    "page": 1,
    "size": 10
  }
}
```

### 6.7 AI群配置管理
```
POST /ai/group/{groupId}/config
```

**说明**: 为群配置 AI 触发规则。仅群主和管理员可操作。

**请求参数**:
```json
{
  "aiId": "ai_001",
  "triggerKeywords": "小助手,AI,助手",
  "triggerProbability": 0.10,
  "isEnabled": true
}
```

**响应**:
```json
{
  "code": 200,
  "message": "配置成功",
  "data": {
    "configId": "cfg_001"
  }
}
```

### 6.8 获取群AI配置列表
```
GET /ai/group/{groupId}/configs
```

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "configId": "cfg_001",
        "aiId": "ai_001",
        "aiName": "小助手",
        "triggerKeywords": "小助手,AI",
        "triggerProbability": 0.10,
        "isEnabled": true
      }
    ]
  }
}
```

---

## 七、WebSocket 接口

### 连接地址
```
ws://localhost:8080/ws?token={jwt_token}
```

### 消息格式
```json
{
  "id": "客户端生成的唯一消息ID（UUID）",
  "type": "消息类型",
  "data": {}
}
```

**说明**: 所有 WebSocket 消息必须携带 `id` 字段（UUID 格式），用于消息对账和重试确认。服务端推送的消息也携带 `id`，客户端可通过该 `id` 确认消息已送达。

### 消息类型

#### 发送消息（客户端 → 服务端）
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "type": "SEND_MESSAGE",
  "data": {
    "sessionId": "p_1001_1002",
    "msgType": "TEXT",
    "content": "你好"
  }
}
```

#### 消息确认（服务端 → 客户端）
```json
{
  "id": "服务端生成的消息ID",
  "type": "MESSAGE_ACK",
  "data": {
    "clientId": "550e8400-e29b-41d4-a716-446655440000",
    "messageId": "msg_001",
    "createTime": "2024-01-01T10:00:00Z"
  }
}
```

#### 接收消息（服务端 → 客户端）
```json
{
  "id": "服务端生成的消息ID",
  "type": "RECEIVE_MESSAGE",
  "data": {
    "messageId": "msg_001",
    "sessionId": "p_1001_1002",
    "senderId": "user_002",
    "senderName": "李四",
    "senderAvatar": "http://...",
    "senderType": "USER",
    "msgType": "TEXT",
    "content": "你好",
    "createTime": "2024-01-01T10:00:00Z"
  }
}
```

#### 群消息（服务端 → 客户端）
```json
{
  "id": "服务端生成的消息ID",
  "type": "GROUP_MESSAGE",
  "data": {
    "messageId": "msg_002",
    "sessionId": "g_2001",
    "groupId": "group_001",
    "senderId": "user_001",
    "senderName": "张三",
    "senderType": "USER",
    "msgType": "TEXT",
    "content": "大家好",
    "mentionUsers": ["user_002"],
    "createTime": "2024-01-01T10:00:00Z"
  }
}
```

#### AI对话请求（客户端 → 服务端）
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440001",
  "type": "AI_CHAT",
  "data": {
    "aiId": "ai_001",
    "sessionId": "a_3001_1001",
    "content": "你好",
    "conversationId": "conv_001"
  }
}
```

#### AI流式响应（服务端 → 客户端）
```json
{
  "id": "服务端生成的消息ID",
  "type": "AI_STREAM",
  "data": {
    "messageId": "msg_ai_001",
    "conversationId": "conv_001",
    "content": "你好！",
    "done": false
  }
}
```

**说明**: `done: true` 表示 AI 回复结束，同时携带完整的 `content`。

#### 在线状态更新（服务端 → 客户端）
```json
{
  "id": "服务端生成的消息ID",
  "type": "STATUS_CHANGE",
  "data": {
    "userId": "user_002",
    "online": true
  }
}
```

#### 消息撤回通知（服务端 → 客户端）
```json
{
  "id": "服务端生成的消息ID",
  "type": "MESSAGE_RECALL",
  "data": {
    "messageId": 100,
    "sessionId": "p_1001_1002",
    "senderId": 1001,
    "recallTime": "2024-01-01T10:05:00Z"
  }
}
```

**说明**: 当有人撤回消息时，服务端推送此通知。私聊时推送给对方和撤回者自己（多端同步），群聊时推送给群内所有成员（排除撤回操作者）。前端收到后应将对应消息的显示内容替换为"对方撤回了一条消息"或"你撤回了一条消息"。

#### 已读通知（服务端 → 客户端）
```json
{
  "id": "服务端生成的消息ID",
  "type": "READ_RECEIPT",
  "data": {
    "sessionId": "p_1001_1002",
    "userId": "user_002",
    "lastReadMessageId": "msg_005",
    "readTime": "2024-01-01T10:30:00Z"
  }
}
```

#### 心跳（双向）
客户端 → 服务端:
```json
{
  "id": "心跳ID",
  "type": "PING",
  "data": {}
}
```

服务端 → 客户端:
```json
{
  "id": "心跳ID",
  "type": "PONG",
  "data": {}
}
```

#### 重连请求（客户端 → 服务端）
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440002",
  "type": "RECONNECT",
  "data": {
    "lastMessageId": 10
  }
}
```

**说明**: `lastMessageId` 为客户端最后收到的消息ID（Long 类型），服务端会补发该ID之后的所有离线消息，然后发送 `RECONNECT_ACK` 确认。

#### 重连确认（服务端 → 客户端）
```json
{
  "id": "服务端生成的消息ID",
  "type": "RECONNECT_ACK",
  "data": {
    "missedCount": 5
  }
}
```

**说明**: `missedCount` 为补发的离线消息数量。

---

## 八、本地服务API（补充）

以下API仅在内嵌本地服务器提供，用于AI隐私数据管理和本地向量检索。

### 8.1 本地健康检查
```
GET /local/health
```

**说明**: 检查本地服务状态，无需认证。

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "status": "UP",
    "database": "H2",
    "vectorStore": "Lucene",
    "uptime": 3600,
    "version": "1.0.0"
  }
}
```

### 8.2 本地配置查询
```
GET /local/config
```

**说明**: 获取本地服务配置信息，无需认证。

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "dataDir": "/Users/xxx/AppData/Voluntary-AI-Chat/data",
    "vectorStoreEnabled": true,
    "embeddingModel": "text-embedding-3-small",
    "maxMemoryCount": 10,
    "memorySimilarityThreshold": 0.7
  }
}
```

### 8.3 向量存储
```
POST /local/vector/store
```

**说明**: 存储向量到本地向量库（Lucene），仅内部服务调用，客户端不直接使用。

**请求参数**:
```json
{
  "id": "mem_001",
  "vector": [0.1, 0.2, 0.3, ...],
  "content": "用户喜欢编程和音乐",
  "metadata": {
    "aiId": "ai_001",
    "userId": "user_001"
  }
}
```

**响应**:
```json
{
  "code": 200,
  "message": "存储成功",
  "data": {
    "vectorId": "vec_001"
  }
}
```

### 8.4 向量检索
```
POST /local/vector/search
```

**说明**: 从本地向量库检索相似向量，仅内部服务调用，客户端不直接使用。

**请求参数**:
```json
{
  "queryVector": [0.1, 0.2, 0.3, ...],
  "aiId": "ai_001",
  "userId": "user_001",
  "topK": 10,
  "minScore": 0.7
}
```

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "results": [
      {
        "id": "mem_001",
        "score": 0.85,
        "content": "用户喜欢编程和音乐",
        "metadata": {
          "aiId": "ai_001",
          "userId": "user_001"
        }
      }
    ]
  }
}
```

### 8.5 向量删除
```
DELETE /local/vector/{vectorId}
```

**说明**: 删除本地向量库中的向量，仅内部服务调用。

**响应**:
```json
{
  "code": 200,
  "message": "删除成功",
  "data": null
}
```

### 8.6 AI会话列表（本地）
```
GET /ai/conversations?page=1&size=20
```

**说明**: 获取AI会话列表（仅本地存储的AI对话），与云端真人会话列表分离。客户端需合并两个列表展示。

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "sessionId": "a_3001_1001",
        "aiId": "ai_001",
        "aiName": "小助手",
        "aiAvatar": "http://localhost:8080/files/ai/001.jpg",
        "lastMessage": "你好！有什么我可以帮助你的吗？",
        "lastMessageType": "TEXT",
        "lastMessageTime": "2024-01-01T10:00:00Z",
        "unreadCount": 0
      }
    ],
    "total": 5,
    "page": 1,
    "size": 20
  }
}
```

### 8.7 本地数据导出
```
GET /local/export
```

**说明**: 导出本地所有AI数据（AI角色、AI对话、AI记忆），用于备份或迁移。

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "exportUrl": "http://localhost:8080/files/export/data_20240101.json",
    "expiresIn": 3600
  }
}
```

### 8.8 本地数据导入
```
POST /local/import
Content-Type: multipart/form-data
```

**说明**: 导入AI数据备份文件，恢复本地AI配置和记忆。

**请求参数**:
```
file: [JSON备份文件]
```

**响应**:
```json
{
  "code": 200,
  "message": "导入成功",
  "data": {
    "aiProfiles": 3,
    "messages": 150,
    "memories": 10
  }
}
```

---

## 九、WebSocket双连接说明

客户端需要维护两个WebSocket连接：

### 云端WebSocket（真人消息）

**连接地址**: `wss://your-cloud-server.com/ws?token={jwt_token}`

**消息类型**:
- `SEND_MESSAGE`：发送真人消息
- `RECEIVE_MESSAGE`：接收私聊消息
- `GROUP_MESSAGE`：接收群聊消息
- `MESSAGE_RECALL`：消息撤回通知
- `READ_RECEIPT`：已读通知
- `STATUS_CHANGE`：在线状态变更
- `PING/PONG`：心跳
- `RECONNECT`：断线重连

### 本地WebSocket（AI消息）

**连接地址**: `ws://localhost:8080/ws?token={jwt_token}`

**消息类型**:
- `AI_CHAT`：发送AI对话请求
- `AI_STREAM`：接收AI流式响应
- `PING/PONG`：心跳

### 双连接协作示例

**群聊场景（真人+AI）**：

1. 用户发送消息 → 客户端同时发送：
   - 云端WebSocket：`SEND_MESSAGE`（真人消息转发给其他群成员）
   - 本地WebSocket：`AI_CHAT`（触发AI回复）

2. AI回复 → 本地WebSocket推送 `AI_STREAM` → 仅本机客户端接收

3. 真人回复 → 云端WebSocket推送 `GROUP_MESSAGE` → 所有在线群成员接收

---

## 十、错误码补充

**本地服务错误码**:
| 错误码 | 模块 | 说明 |
|--------|------|------|
| 6001 | 本地服务 | 本地服务未启动 |
| 6002 | 本地服务 | 数据目录权限不足 |
| 6003 | 本地服务 | 向量库初始化失败 |
| 6004 | 本地服务 | 数据导入格式错误 |
| 6005 | 本地服务 | 备份文件已过期 |
