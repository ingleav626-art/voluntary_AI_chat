# API 接口文档

> RESTful API 和 WebSocket 接口规范

---

## 基础信息

**Base URL**: `http://localhost:8080/api`

**认证方式**: JWT Token（除登录注册外，所有接口需要携带Token）

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

**错误码**:
| 错误码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未认证 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 500 | 服务器错误 |

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
  "message": "注册成功",
  "data": {
    "userId": "user_001",
    "username": "张三"
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
  "message": "登录成功",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "expiresIn": 7200,
    "user": {
      "userId": "user_001",
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
    "expiresIn": 7200
  }
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
    "userId": "user_001",
    "phone": "138****8000",
    "username": "张三",
    "avatar": "http://minio.example.com/avatar/001.jpg",
    "bio": "这个人很懒，什么都没写",
    "createTime": "2024-01-01T00:00:00Z"
  }
}
```

### 2.2 修改个人信息
```
PUT /user/profile
```

**请求参数**:
```json
{
  "username": "张三三",
  "avatar": "http://minio.example.com/avatar/002.jpg",
  "bio": "我是一个程序员"
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

### 2.3 搜索用户
```
GET /user/search?keyword=张三
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
    ]
  }
}
```

---

## 三、好友模块

### 3.1 发送好友申请
```
POST /friend/apply
```

**请求参数**:
```json
{
  "targetUserId": "user_002",
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

### 4.1 获取聊天记录
```
GET /message/history?targetId=user_002&page=1&size=20
```

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "messageId": "msg_001",
        "senderId": "user_001",
        "senderName": "张三",
        "senderAvatar": "http://minio.example.com/avatar/001.jpg",
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

### 4.2 发送消息（REST）
```
POST /message/send
```

**请求参数**:
```json
{
  "targetId": "user_002",
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

### 4.3 撤回消息
```
POST /message/recall
```

**请求参数**:
```json
{
  "messageId": "msg_001"
}
```

**响应**:
```json
{
  "code": 200,
  "message": "已撤回",
  "data": null
}
```

### 4.4 上传图片
```
POST /message/upload/image
Content-Type: multipart/form-data
```

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
    "url": "http://minio.example.com/chat/image/001.jpg",
    "thumbnailUrl": "http://minio.example.com/chat/image/001_thumb.jpg",
    "width": 1920,
    "height": 1080,
    "size": 1024000
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
  "memberIds": ["user_002", "user_003", "user_004"]
}
```

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
GET /group/list
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
    ]
  }
}
```

### 5.3 获取群成员
```
GET /group/{groupId}/members
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
    ]
  }
}
```

### 5.4 修改群信息
```
PUT /group/{groupId}
```

**请求参数**:
```json
{
  "name": "新群名",
  "announcement": "群公告内容"
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

---

## 六、AI模块

### 6.1 获取AI列表
```
GET /ai/list
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
        "model": "gpt-4"
      }
    ]
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
  "apiKey": "sk-xxx"
}
```

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

### 6.3 AI对话
```
POST /ai/chat
```

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

### 6.4 获取AI记忆
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
  "type": "消息类型",
  "data": {}
}
```

### 消息类型

#### 发送消息
```json
{
  "type": "SEND_MESSAGE",
  "data": {
    "targetId": "user_002",
    "type": "TEXT",
    "content": "你好"
  }
}
```

#### 接收消息
```json
{
  "type": "RECEIVE_MESSAGE",
  "data": {
    "messageId": "msg_001",
    "senderId": "user_002",
    "senderName": "李四",
    "senderAvatar": "http://...",
    "type": "TEXT",
    "content": "你好",
    "createTime": "2024-01-01T10:00:00Z"
  }
}
```

#### 群消息
```json
{
  "type": "GROUP_MESSAGE",
  "data": {
    "messageId": "msg_002",
    "groupId": "group_001",
    "senderId": "user_001",
    "senderName": "张三",
    "type": "TEXT",
    "content": "大家好",
    "mentionUsers": ["user_002"],
    "createTime": "2024-01-01T10:00:00Z"
  }
}
```

#### AI流式响应
```json
{
  "type": "AI_STREAM",
  "data": {
    "messageId": "msg_ai_001",
    "conversationId": "conv_001",
    "content": "你好！",
    "done": false
  }
}
```

#### 在线状态更新
```json
{
  "type": "STATUS_CHANGE",
  "data": {
    "userId": "user_002",
    "online": true
  }
}
```

#### 心跳
```json
{
  "type": "PING",
  "data": {}
}
```

响应:
```json
{
  "type": "PONG",
  "data": {}
}
```
