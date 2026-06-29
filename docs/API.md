# API 接口文档

> 云端 RESTful API + WebSocket 实时通信接口规范

---

## 架构说明

本系统采用**双通道架构**：

| 通道 | 用途 | 技术栈 |
|------|------|--------|
| **云端 REST API** | 认证、用户、好友、群组、消息管理 | HTTPS + JSON |
| **云端 WebSocket** | 真人实时消息、在线状态、消息已读 | WSS + JSON |
| **本地 AI 引擎** | AI 对话、AI 角色管理、AI 记忆 | 本地 POJO 方法调用 |

> **客户包不启动内嵌服务器**。AI 操作通过 `LocalAiEngine` 直接方法调用，不走 HTTP/WS 回环。
> REST API / WebSocket 仅用于与云端服务器通信（真人聊天、用户管理等功能）。

---

## 基础信息

### Base URL

```
https://your-cloud-server.com/api
```

### 认证方式

JWT Bearer Token（登录后获取，通过 `Authorization` 请求头传递）：

```
Authorization: Bearer <accessToken>
Content-Type: application/json
```

### 通用响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

### 分页格式

**请求参数**：`?page=1&size=20`（page 从 1 开始，size 最大 100）

**响应**：
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

### 会话 ID 生成规则

| 场景 | 格式 | 示例 |
|------|------|------|
| 单聊 | `p_{min}_{max}` | `p_1001_1002` |
| 群聊 | `g_{groupId}` | `g_2001` |

### HTTP 状态码

| 状态码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未认证 / Token 过期 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

### 业务错误码

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
| 4001 | 消息 | 消息已超过 2 分钟，不可撤回 |
| 4002 | 消息 | 无权撤回他人消息 |
| 4003 | 消息 | 图片格式不支持 |
| 4004 | 消息 | 图片大小超出限制 |
| 5001 | AI | API Key 无效 |
| 5002 | AI | AI 不存在或已禁用 |

---

## 一、认证模块 `/api/auth`

### 1.1 发送验证码

```
POST /api/auth/sms/send
```

**请求**：
```json
{
  "phone": "13800138000"
}
```

**响应**：`code: 200, data: null`

---

### 1.2 用户注册

```
POST /api/auth/register
```

注册成功后自动登录，返回完整登录态。

**请求**：
```json
{
  "phone": "13800138000",
  "code": "123456",
  "username": "张三",
  "password": "password123"
}
```

**响应**：
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
      "avatar": "http://your-server.com/files/avatar/001.jpg"
    }
  }
}
```

---

### 1.3 用户登录

```
POST /api/auth/login
```

**请求**：
```json
{
  "phone": "13800138000",
  "password": "password123",
  "rememberMe": true
}
```

**响应**：同注册接口，返回 accessToken + refreshToken + user

---

### 1.4 刷新 Token

```
POST /api/auth/refresh
```

**请求**：
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

**响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "expiresIn": 7200
  }
}
```

---

### 1.5 忘记密码

```
POST /api/auth/forgot-password
```

**请求**：
```json
{
  "phone": "13800138000",
  "code": "123456",
  "newPassword": "newpassword123"
}
```

---

## 二、用户模块 `/api/user`

### 2.1 获取个人信息

```
GET /api/user/profile
```

**响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "userId": 1001,
    "phone": "138****8000",
    "username": "张三",
    "avatar": "http://your-server.com/files/avatar/001.jpg",
    "gender": 1,
    "bio": "你好，我是张三",
    "age": 25,
    "birthday": "2000-01-01",
    "detailBio": "这是我的详细介绍"
  }
}
```

### 2.2 更新个人信息

```
PUT /api/user/profile
```

**请求**：
```json
{
  "username": "张三",
  "avatar": "http://your-server.com/files/avatar/002.jpg",
  "gender": 1,
  "bio": "新签名",
  "age": 25,
  "birthday": "2000-01-01",
  "detailBio": "这是我的详细介绍"
}
```

所有字段均为可选，仅传需要修改的字段即可。

### 2.3 搜索用户

```
GET /api/user/search?keyword=张三&page=1&size=20
```

### 2.4 修改密码

```
PUT /api/user/password
```

**请求**：
```json
{
  "oldPassword": "old123",
  "newPassword": "new456"
}
```

### 2.5 修改手机号

```
PUT /api/user/phone
```

**请求**：
```json
{
  "newPhone": "13900139000",
  "code": "123456"
}
```

---

## 三、好友模块 `/api/friend`

### 3.1 发送好友申请

```
POST /api/friend/apply
```

**请求**：
```json
{
  "targetUserId": 1002,
  "remark": "我是张三"
}
```

### 3.2 获取好友申请列表

```
GET /api/friend/apply/list
```

**响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "received": [
      {
        "applyId": 1,
        "applicantId": 1002,
        "applicantName": "李四",
        "applicantAvatar": "...",
        "remark": "我是李四",
        "status": "PENDING",
        "createdAt": "2026-06-01T12:00:00"
      }
    ],
    "sent": []
  }
}
```

### 3.3 处理好友申请

```
POST /api/friend/apply/{applyId}/handle
```

**请求**：
```json
{
  "status": "ACCEPTED"
}
```

`status` 可选值：`ACCEPTED`、`REJECTED`

### 3.4 获取好友列表

```
GET /api/friend/list
```

**响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "friendId": 1002,
      "username": "李四",
      "avatar": "...",
      "onlineStatus": "ONLINE",
      "lastActiveTime": "2026-06-26T10:00:00"
    }
  ]
}
```

### 3.5 删除好友

```
DELETE /api/friend/{friendId}
```

---

## 四、群组模块 `/api/group`

### 4.1 创建群组

```
POST /api/group/create
```

**请求**：
```json
{
  "groupName": "技术交流群",
  "avatar": "...",
  "announcement": "欢迎加入",
  "maxMembers": 100
}
```

### 4.2 获取群列表

```
GET /api/group/list
```

### 4.3 获取群成员

```
GET /api/group/{groupId}/members
```

### 4.4 更新群信息

```
PUT /api/group/{groupId}
```

**请求**：
```json
{
  "groupName": "新群名",
  "avatar": "...",
  "announcement": "新公告"
}
```

### 4.5 邀请成员

```
POST /api/group/{groupId}/invite
```

**请求**：
```json
{
  "memberIds": [1002, 1003]
}
```

### 4.6 移除成员

```
DELETE /api/group/{groupId}/members/{targetUserId}
```

需要群主或管理员权限。

### 4.7 退出群组

```
POST /api/group/{groupId}/leave
```

### 4.8 转让群主

```
POST /api/group/{groupId}/transfer
```

**请求**：
```json
{
  "newOwnerId": 1002
}
```

### 4.9 解散群组

```
DELETE /api/group/{groupId}
```

需要群主权限。

### 4.10 设置管理员

```
POST /api/group/{groupId}/admin
```

**请求**：
```json
{
  "userId": 1002,
  "action": "SET"    // SET 或 UNSET
}
```

### 4.11 设置群昵称

```
PUT /api/group/{groupId}/nickname
```

**请求**：
```json
{
  "nickname": "我的群昵称"
}
```

---

## 五、消息模块 `/api/message`

### 5.1 发送消息

```
POST /api/message/send
```

**请求**：
```json
{
  "sessionId": "p_1001_1002",
  "content": "你好！",
  "contentType": "TEXT",
  "replyToId": null
}
```

`contentType` 可选值：`TEXT`、`IMAGE`

### 5.2 上传图片

```
POST /api/message/upload/image
Content-Type: multipart/form-data

file: <binary>
```

**响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": "http://your-server.com/files/chat/images/001.jpg"
}
```

### 5.3 获取聊天历史

```
GET /api/message/history?sessionId=p_1001_1002&page=1&size=20
```

### 5.4 撤回消息

```
POST /api/message/recall
```

**请求**：
```json
{
  "messageId": 5001,
  "sessionId": "p_1001_1002"
}
```

仅 2 分钟内可撤回，仅消息发送者可撤回。

### 5.5 消息已读

```
POST /api/message/read
```

**请求**：
```json
{
  "sessionId": "p_1001_1002",
  "lastMessageId": 5001
}
```

---

## 六、会话模块 `/api/conversation`

### 6.1 获取会话列表

```
GET /api/conversation/list
```

**响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "sessionId": "p_1001_1002",
      "type": "PRIVATE",
      "targetId": 1002,
      "targetName": "李四",
      "targetAvatar": "...",
      "lastMessage": "你好！",
      "lastMessageAt": "2026-06-26T10:00:00",
      "unreadCount": 3,
      "onlineStatus": "ONLINE"
    }
  ]
}
```

---

## 七、WebSocket 实时通信

### 连接地址

```
wss://your-cloud-server.com/ws?token=<accessToken>
```

### 消息格式

所有 WebSocket 消息遵循统一 JSON 格式：

```json
{
  "type": "SEND_MESSAGE",
  "timestamp": "2026-06-26T10:00:00",
  "payload": {}
}
```

### 消息类型

| type | 方向 | 说明 |
|------|------|------|
| `SEND_MESSAGE` | 客户端 → 服务端 | 发送消息 |
| `RECEIVE_MESSAGE` | 服务端 → 客户端 | 接收消息 |
| `GROUP_MESSAGE` | 双向 | 群聊消息 |
| `MESSAGE_ACK` | 服务端 → 客户端 | 消息送达确认 |
| `MESSAGE_RECALL` | 双向 | 消息撤回通知 |
| `READ_RECEIPT` | 双向 | 已读回执 |
| `STATUS_CHANGE` | 服务端 → 客户端 | 用户在线状态变更 |
| `PING` | 客户端 → 服务端 | 心跳 |
| `PONG` | 服务端 → 客户端 | 心跳响应 |
| `RECONNECT` | 客户端 → 服务端 | 断线重连请求 |
| `RECONNECT_ACK` | 服务端 → 客户端 | 重连确认 |
| `FORCE_LOGOUT` | 服务端 → 客户端 | 强制下线通知 |
| `GROUP_MEMBER_JOIN` | 服务端 → 客户端 | 新成员加入群 |
| `GROUP_MEMBER_LEAVE` | 服务端 → 客户端 | 成员退出群 |
| `GROUP_MEMBER_ROLE_CHANGE` | 服务端 → 客户端 | 成员角色变更 |
| `GROUP_INFO_CHANGE` | 服务端 → 客户端 | 群信息变更 |
| `GROUP_DISMISSED` | 服务端 → 客户端 | 群被解散 |

### 消息 payload 格式

**SEND_MESSAGE / RECEIVE_MESSAGE**
```json
{
  "type": "SEND_MESSAGE",
  "payload": {
    "messageId": 5001,
    "sessionId": "p_1001_1002",
    "senderId": 1001,
    "content": "你好！",
    "contentType": "TEXT",
    "timestamp": "2026-06-26T10:00:00",
    "replyToId": null,
    "status": "SENT"
  }
}
```

**GROUP_MESSAGE**
```json
{
  "type": "GROUP_MESSAGE",
  "payload": {
    "messageId": 6001,
    "groupId": 2001,
    "sessionId": "g_2001",
    "senderId": 1001,
    "content": "大家好！",
    "contentType": "TEXT",
    "timestamp": "2026-06-26T10:00:00"
  }
}
```

**STATUS_CHANGE**
```json
{
  "type": "STATUS_CHANGE",
  "payload": {
    "userId": 1002,
    "onlineStatus": "ONLINE",
    "lastActiveTime": "2026-06-26T10:00:00"
  }
}
```

**FORCE_LOGOUT**
```json
{
  "type": "FORCE_LOGOUT",
  "payload": {
    "message": "您的账号已在其他设备登录"
  }
}
```

**AI_CHAT**（客户端 → 服务端，触发 AI 流式回复）
```json
{
  "type": "AI_CHAT",
  "payload": {
    "aiId": 3001,
    "content": "你好，给我讲个笑话",
    "sessionId": "p_1001_3001"
  }
}
```

**AI_STREAM**（服务端 → 客户端，AI 流式回复分块）
```json
{
  "type": "AI_STREAM",
  "payload": {
    "aiId": 3001,
    "content": "好的，",
    "done": false,
    "messageId": 7001
  }
}
```

最后一个分块 `done: true`，`content` 为空字符串，表示流式回复结束。

---

## 八、本地 AI 引擎 API（客户包独有）

**客户包不启动内嵌服务器**。以下 API 通过 `LocalAiEngine` POJO 方法调用实现。

### 调用方式

```java
// 获取单例
LocalAiEngine engine = LocalAiEngine.getInstance();

// AI 角色列表
List<AiProfile> profiles = engine.listAiProfiles();

// AI 对话 (流式)
engine.chat(request, new AiStreamCallback() {
    @Override public void onChunk(String chunk) {
        Platform.runLater(() -> appendText(chunk));
    }
    @Override public void onComplete() {
        Platform.runLater(() -> finalizeMessage());
    }
    @Override public void onError(Throwable e) {
        Platform.runLater(() -> showError(e));
    }
});
```

### 接口列表

| 方法 | 说明 |
|------|------|
| `listAiProfiles()` | 获取所有 AI 角色 |
| `getAiProfile(Long id)` | 获取单个 AI 角色 |
| `createAiProfile(CreateAiProfileRequest req)` | 创建 AI 角色 |
| `updateAiProfile(Long id, UpdateAiProfileRequest req)` | 更新 AI 角色 |
| `deleteAiProfile(Long id)` | 删除 AI 角色 |
| `chat(AiChatRequest req, AiStreamCallback cb)` | AI 对话（流式） |
| `listMemories(Long aiId)` | 获取 AI 记忆 |
| `summarizeMemory(Long aiId)` | 触发记忆概括 |
| `getGroupConfigs(Long groupId)` | 获取群 AI 配置 |
| `updateGroupConfig(Long groupId, AiGroupConfig config)` | 更新群 AI 配置 |
| `shutdown()` | 关闭引擎（释放 H2 连接） |

### 测试包/云端包中的 AI API（REST）

当使用测试包（内嵌 Spring Boot）或云端包时，AI 功能也通过 REST API 提供（与本地 AI 引擎功能一致）：

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/ai/list` | 获取 AI 角色列表 |
| POST | `/api/ai/create` | 创建 AI 角色 |
| PUT | `/api/ai/{aiId}` | 更新 AI 角色 |
| DELETE | `/api/ai/{aiId}` | 删除 AI 角色 |
| POST | `/api/ai/group/{groupId}/config` | 更新群 AI 配置 |
| GET | `/api/ai/group/{groupId}/configs` | 获取群 AI 配置 |
| GET | `/api/ai/{aiId}/memories` | 获取 AI 记忆 |

### 本地健康检查端点（ai-core 模块）

嵌入式模式下的健康检查端点，由 `LocalController` 提供：

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/local/health` | 健康检查（返回 `"OK"`） |

---

## 九、错误处理

### 成功响应 (code=200)

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

### 业务错误响应 (code≠200)

```json
{
  "code": 1001,
  "message": "手机号已注册",
  "data": null
}
```

### 认证错误 (HTTP 401)

```json
{
  "code": 401,
  "message": "Token 已过期，请重新登录",
  "data": null
}
```

### 参数校验失败 (HTTP 400)

```json
{
  "code": 400,
  "message": "参数校验失败",
  "data": {
    "phone": "手机号格式不正确",
    "password": "密码长度不能少于6位"
  }
}
```