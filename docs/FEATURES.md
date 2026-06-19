# 功能规格文档

> 详细描述每个功能的需求、设计和实现要点

---

## 一、用户系统

### 1.1 用户注册

**需求描述**：
- 用户可以通过手机号注册账号
- 需要短信验证码验证
- 注册时设置用户名和密码

**界面设计**：
- 注册页面包含：手机号、验证码、用户名、密码、确认密码
- 表单验证：手机号格式、密码强度、用户名唯一性

**接口定义**：
```
POST /api/auth/register
Request: { phone, code, username, password }
Response: { success, message, userId }
```

**实现要点**：
- 密码使用 BCrypt 加密存储
- 验证码有效期5分钟
- 用户名唯一性检查
- 手机号唯一性检查

### 1.2 用户登录

**需求描述**：
- 用户可以通过手机号+密码登录
- 支持记住密码功能
- 登录成功后获取 JWT Token

**界面设计**：
- 登录页面包含：手机号、密码、记住我
- 登录按钮和注册链接

**接口定义**：
```
POST /api/auth/login
Request: { phone, password, rememberMe }
Response: { success, token, refreshToken, user }
```

**实现要点**：
- JWT Token 有效期2小时
- Refresh Token 有效期7天
- 登录失败次数限制（5次锁定30分钟）
- 在线状态更新

### 1.3 用户信息

**需求描述**：
- 用户可以查看和修改个人信息
- 支持修改头像、昵称、签名

**接口定义**：
```
GET /api/user/profile
Response: { userId, username, avatar, bio, createTime }

PUT /api/user/profile
Request: { username, avatar, bio }
Response: { success, message }
```

---

## 二、好友系统

### 2.1 好友申请

**需求描述**：
- 用户可以搜索其他用户并发送好友申请
- 支持验证消息
- 对方可以同意或拒绝

**界面设计**：
- 搜索框：输入用户名或手机号
- 好友申请列表：显示待处理的申请
- 申请详情：申请人信息、验证消息

**接口定义**：
```
POST /api/friend/apply
Request: { targetUserId, message }
Response: { success, message }

GET /api/friend/apply/list
Response: { success, list: [{ applyId, userId, username, avatar, message, createTime }] }
```

### 2.2 好友管理

**需求描述**：
- 查看好友列表
- 删除好友
- 设置好友备注

**界面设计**：
- 好友列表：按字母分组
- 好友详情：头像、昵称、签名
- 操作菜单：发消息、删除好友、设置备注

**接口定义**：
```
GET /api/friend/list
Response: { success, list: [{ userId, username, avatar, bio, remark }] }

DELETE /api/friend/{friendId}
Response: { success, message }

PUT /api/friend/remark
Request: { friendId, remark }
Response: { success, message }
```

---

## 三、单聊功能

### 3.1 文字消息

**需求描述**：
- 发送和接收文字消息
- 支持表情符号
- 消息已读状态

**界面设计**：
- 聊天气泡：区分自己和对方
- 时间戳：显示发送时间
- 输入框：支持换行和表情

**消息格式**：
```json
{
  "type": "TEXT",
  "content": "你好",
  "timestamp": 1234567890,
  "messageId": "msg_001"
}
```

### 3.2 图片消息

**需求描述**：
- 发送和接收图片
- 支持图片预览和下载
- 图片压缩和缩略图

**界面设计**：
- 图片气泡：点击放大
- 上传进度条
- 图片预览弹窗

**实现要点**：
- 图片上传到 MinIO
- 生成缩略图
- 图片压缩（最大宽度1080px）

### 3.3 消息撤回

**需求描述**：
- 2分钟内可撤回消息
- 撤回后显示"对方撤回了一条消息"
- AI消息可随时撤回

**接口定义**：
```
POST /api/message/recall
Request: { messageId }
Response: { success, message }
```

### 3.4 消息转发

**需求描述**：
- 支持单条消息转发
- 支持多条消息合并转发
- 转发时显示原发送者

**界面设计**：
- 长按消息弹出菜单
- 转发选择联系人/群
- 转发确认弹窗

---

## 四、群聊功能

### 4.1 群组管理

**需求描述**：
- 创建群组（选择好友）
- 解散群组（仅群主）
- 修改群信息（名称、公告）

**角色权限**：
| 操作 | 群主 | 管理员 | 普通成员 |
|------|------|--------|---------|
| 修改群信息 | ✅ | ❌ | ❌ |
| 发布公告 | ✅ | ✅ | ❌ |
| 踢人 | ✅ | ✅ | ❌ |
| 邀请成员 | ✅ | ✅ | ✅ |
| 解散群组 | ✅ | ❌ | ❌ |

### 4.2 群消息

**需求描述**：
- 群内所有人可见
- 支持@功能
- 消息已读状态

**消息格式**：
```json
{
  "type": "GROUP_TEXT",
  "groupId": "group_001",
  "content": "大家好",
  "mentionUsers": ["user_002", "user_003"],
  "timestamp": 1234567890
}
```

### 4.3 多AI群（特色功能）

**需求描述**：
- 群内可挂载多个AI
- 每个AI有独立人设
- 支持@特定AI
- 未@时按规则触发AI

**AI触发策略**：
- 优先级：@AI > 关键词匹配 > 概率触发
- 概率触发：默认10%概率
- 关键词匹配：配置关键词列表

**AI人设配置**：
```json
{
  "aiId": "ai_001",
  "name": "小助手",
  "persona": "你是一个友好的助手",
  "modelProvider": "openai",
  "model": "gpt-4",
  "triggers": ["小助手", "AI"]
}
```

---

## 五、AI功能

### 5.1 AI对话

**需求描述**：
- 用户可以与AI进行对话
- 支持多轮对话
- 支持流式输出

**界面设计**：
- AI聊天界面与普通聊天类似
- 显示AI人设信息
- 流式输出效果

**接口定义**：
```
POST /api/ai/chat
Request: { aiId, content, conversationId }
Response: { success, content, conversationId }

WebSocket: /ws/ai/stream
Message: { type: "AI_STREAM", content: "partial...", done: false }
```

### 5.2 AI主动聊天（特色功能）

**需求描述**：
- AI可以主动发起聊天
- 基于用户兴趣和记忆
- 随机时间间隔触发

**触发机制**：
```
定时任务(每用户随机间隔5-30分钟)
→ 检查用户是否在线
→ 读取最近记忆摘要
→ 生成主动聊天提示词
→ 调用AI生成消息
→ 推送给用户
```

**配置项**：
```yaml
ai:
  proactive:
    enabled: true
    min-interval: 5m
    max-interval: 30m
    memory-threshold: 20  # 聊天条数达到20条触发记忆总结
```

### 5.3 记忆管理（特色功能）

**需求描述**：
- AI可以记住与用户的对话
- 基于记忆进行个性化回复
- 支持记忆检索（RAG）

**记忆流程**：
```
聊天达到阈值(20条)
→ 异步任务总结为"日记"
→ 存储MySQL(摘要) + Milvus(向量)
→ 下次对话时检索相关记忆
→ 拼入Prompt生成回复
```

**记忆数据结构**：
```json
{
  "memoryId": "mem_001",
  "aiId": "ai_001",
  "userId": "user_001",
  "summary": "用户喜欢编程和音乐",
  "keywords": ["编程", "音乐", "Java"],
  "createTime": "2024-01-01T00:00:00Z"
}
```

---

## 六、本地模型（特色功能）

### 6.1 句子完整性检测

**需求描述**：
- 客户端本地检测句子是否完整
- 不完整的句子不发送
- 保护用户隐私（文字不上传）

**模型要求**：
- 模型大小 < 50MB
- 推理延迟 < 100ms
- 准确率 > 90%

**检测逻辑**：
```java
public boolean isComplete(String text) {
    // 1. 空文本不发送
    if (text.isEmpty()) return false;
    
    // 2. 本地模型检测
    boolean modelResult = localModel.predict(text);
    
    // 3. 规则补充（问号、感叹号结尾）
    if (text.endsWith("？") || text.endsWith("！")) return true;
    
    return modelResult;
}
```

---

## 七、安全功能

### 7.1 API Key管理

**需求描述**：
- 用户的API Key加密存储
- 使用时解密调用
- 支持多个AI服务商

**加密方案**：
- 算法：AES-256-GCM
- 密钥派生：PBKDF2(用户密码)
- 存储：数据库加密字段

### 7.2 消息加密

**需求描述**：
- WebSocket通信加密（WSS）
- 敏感消息端到端加密（可选）
- 消息存储加密

---

## 八、性能要求

### 8.1 响应时间
- 登录响应：< 500ms
- 消息发送：< 200ms
- AI回复（首token）：< 2s
- 图片上传：< 3s（10MB以内）

### 8.2 并发能力
- 支持1000+在线用户
- 支持100+群同时聊天
- 消息吞吐量：1000条/秒

### 8.3 资源占用
- 客户端内存：< 500MB
- 客户端安装包：< 200MB
- 本地模型大小：< 50MB
