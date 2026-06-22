# 群聊模块开发计划书（服务端）

> 基于现有后端代码完成度分析，制定服务端群聊模块补齐计划

---

## 一、现有代码完成度评估

### ✅ 服务端 — 已完成清单

| 模块 | 文件 | 状态 |
|------|------|------|
| 数据库表 | `schema.sql` → `group` 表、`group_member` 表 | ✅ 已完成 |
| 实体类 | `GroupEntity.java`、`GroupMember.java` | ✅ 已完成 |
| Mapper | `GroupMapper.java`、`GroupMemberMapper.java`（含5个自定义查询） | ✅ 已完成 |
| DTO 请求 | `CreateGroupRequest`、`UpdateGroupRequest`、`InviteMemberRequest` | ✅ 已完成 |
| DTO 响应 | `GroupResponse`、`GroupMemberResponse`、`CreateGroupResponse` | ✅ 已完成 |
| Service | `GroupService.java`（401行，7个业务方法） | ✅ 已完成 |
| Controller | `GroupController.java`（7个REST端点） | ✅ 已完成 |
| 单元测试 | `GroupServiceTest.java`（420行，14个测试用例） | ✅ 已完成 |
| Controller测试 | `GroupControllerTest.java`（190行，7个测试用例） | ✅ 已完成 |
| 群消息支持 | `ChatWebSocketHandler.java` → 群消息广播 `broadcastGroupMessage()`、群消息撤回广播 | ✅ 已完成 |
| 消息撤回逻辑 | `MessageService.java` → 群消息撤回权限校验（管理员可撤回他人消息） | ✅ 已完成 |
| 会话列表 | `ConversationService.java` → 支持群组 `g_` 格式会话构建 | ✅ 已完成 |
| 离线补发 | `MessageService.getOfflineMessages()` → 群消息离线补发 | ✅ 已完成 |
| 公共枚举 | `GroupRole`（MEMBER/ADMIN/OWNER）、`ErrorCode` 群组错误码（3001-3003） | ✅ 已完成 |

### ✅ 服务端群聊已有 REST 端点一览

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/api/group/create` | 创建群组 | 登录用户 |
| GET | `/api/group/list` | 获取群列表（分页） | 登录用户 |
| GET | `/api/group/{groupId}/members` | 获取群成员（分页） | 登录用户 |
| PUT | `/api/group/{groupId}` | 修改群信息 | 群主 |
| POST | `/api/group/{groupId}/invite` | 邀请成员 | 群成员 |
| DELETE | `/api/group/{groupId}/members/{targetUserId}` | 移除成员 | 群主/管理员 |
| POST | `/api/group/{groupId}/leave` | 退出群组 | 群成员（群主不可退出） |

---

## 二、待补充功能

| 功能 | 说明 | 优先级 |
|------|------|--------|
| `转让群主` | 群主可将所有权转给其他群成员 | 高 |
| `解散群组` | 群主解散群组，逻辑删除群组及所有成员 | 高 |
| `设置/取消管理员` | 群主任命或撤销管理员 | 中 |
| `群成员昵称` | 成员可设置自己在群中的昵称 | 低 |
| `群事件 WebSocket 推送` | 成员加入/离开/角色变更时推送给群内所有人 | 高 |

---

## 三、新增 REST 接口设计

### 3.1 转让群主

```
POST /api/group/{groupId}/transfer
```

**说明**: 仅群主可操作。转让后原群主自动变为普通成员，被转让者变为群主。

**请求参数**:
```json
{
  "targetUserId": 1002
}
```

**响应**: `200 { "code": 200, "message": "转让成功", "data": null }`

**Service 逻辑**:
1. 校验操作者为群主
2. 校验目标用户为群成员
3. 将群 `owner_id` 改为目标用户
4. 将原群主角色改为 `MEMBER`，目标用户角色改为 `OWNER`

---

### 3.2 解散群组

```
DELETE /api/group/{groupId}
```

**说明**: 仅群主可操作。逻辑删除群组及所有成员记录。

**响应**: `200 { "code": 200, "message": "群组已解散", "data": null }`

**Service 逻辑**:
1. 校验操作者为群主
2. 逻辑删除群组（`is_deleted = 1`）
3. 逻辑删除所有群成员（`is_deleted = 1`）

---

### 3.3 设置/取消管理员

```
POST /api/group/{groupId}/admin
```

**说明**: 仅群主可操作。通过 `action` 参数控制设置或取消。

**请求参数**:
```json
{
  "targetUserId": 1003,
  "action": "SET"
}
```

**action 取值**: `SET`（设为管理员）、`REMOVE`（取消管理员）

**响应**: `200 { "code": 200, "message": "操作成功", "data": null }`

**Service 逻辑**:
1. 校验操作者为群主
2. 校验目标用户为群成员
3. `SET` → 将目标角色改为 `ADMIN`
4. `REMOVE` → 将目标角色改回 `MEMBER`
5. 不能对群主操作，不能重复设置

---

### 3.4 设置群成员昵称

```
PUT /api/group/{groupId}/nickname
```

**说明**: 群成员可设置自己在群中的显示昵称。

**请求参数**:
```json
{
  "nickname": "我的群昵称"
}
```

**响应**: `200 { "code": 200, "message": "设置成功", "data": null }`

**Service 逻辑**:
1. 校验操作者为群成员
2. 更新 `group_member.nickname` 字段

---

## 四、新增 DTO 类

### 4.1 TransferOwnerRequest.java

```java
package com.voluntary.chat.server.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TransferOwnerRequest {
    @NotNull(message = "目标用户ID不能为空")
    private Long targetUserId;
}
```

### 4.2 AdminActionRequest.java

```java
package com.voluntary.chat.server.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminActionRequest {
    @NotNull(message = "目标用户ID不能为空")
    private Long targetUserId;

    @NotBlank(message = "操作类型不能为空")
    private String action;  // SET / REMOVE
}
```

### 4.3 SetNicknameRequest.java

```java
package com.voluntary.chat.server.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SetNicknameRequest {
    @Size(max = 50, message = "昵称不能超过50字符")
    private String nickname;
}
```

---

## 五、WebSocket 通知增强

> 以下场景需要在操作成功后通过 WebSocket 推送通知给相关群成员

### 5.1 新增消息类型常量（MessageTypes.java）

```java
// 群组事件通知
public static final String GROUP_MEMBER_JOIN = "GROUP_MEMBER_JOIN";
public static final String GROUP_MEMBER_LEAVE = "GROUP_MEMBER_LEAVE";
public static final String GROUP_MEMBER_ROLE_CHANGE = "GROUP_MEMBER_ROLE_CHANGE";
public static final String GROUP_INFO_CHANGE = "GROUP_INFO_CHANGE";
public static final String GROUP_DISMISSED = "GROUP_DISMISSED";
```

### 5.2 WebSocket 推送规范

| WebSocket 类型 | 推送场景 | 接收方 | data 结构 |
|---------------|---------|--------|-----------|
| `GROUP_MEMBER_JOIN` | 新成员加入（邀请成功时） | 群内所有成员 | `{ groupId, userId, username, avatar }` |
| `GROUP_MEMBER_LEAVE` | 成员退出/被移除 | 群内所有成员（除操作者外） | `{ groupId, userId, username }` |
| `GROUP_MEMBER_ROLE_CHANGE` | 成员角色变更（设管理员/转让群主） | 群内所有成员 | `{ groupId, userId, username, role }` |
| `GROUP_INFO_CHANGE` | 群信息变更（名称/公告） | 群内所有成员 | `{ groupId, name?, announcement? }` |
| `GROUP_DISMISSED` | 群组被解散 | 群内所有成员 | `{ groupId }` |

---

## 六、文件变更清单

### 修改文件

| 变更类型 | 文件路径 | 变更说明 |
|---------|---------|---------|
| 修改 | `common/.../constant/MessageTypes.java` | 新增5个群事件 WebSocket 消息类型常量 |
| 新建 | `server/.../dto/request/TransferOwnerRequest.java` | 转让群主请求 DTO |
| 新建 | `server/.../dto/request/AdminActionRequest.java` | 管理员操作请求 DTO |
| 新建 | `server/.../dto/request/SetNicknameRequest.java` | 设置昵称请求 DTO |
| 修改 | `server/.../service/GroupService.java` | 新增4个业务方法（注意文件行数限制 ≤ 400行，可能需要拆分） |
| 修改 | `server/.../controller/GroupController.java` | 新增4个 REST 端点 |
| 修改 | `server/.../websocket/ChatWebSocketHandler.java` | 新增群事件广播方法（已有 `broadcastToGroupExcept` 可复用） |
| 修改 | `server/.../service/GroupServiceTest.java` | 补充新增功能的单元测试 |
| 修改 | `server/.../controller/GroupControllerTest.java` | 补充新增端点的测试 |

---

## 七、测试要求

### 7.1 GroupService 新增测试用例

| 测试方法 | 场景 | 验证点 |
|---------|------|--------|
| `transferOwner_shouldSucceed` | 群主转让所有权给群成员 | 群主角色变为MEMBER，目标角色变为OWNER，群ownerId更新 |
| `transferOwner_shouldThrow_whenNotOwner` | 非群主尝试转让 | 抛出 NO_PERMISSION |
| `transferOwner_shouldThrow_whenTargetNotMember` | 目标用户不在群中 | 抛出 NOT_FOUND |
| `dismissGroup_shouldSucceed` | 群主解散群组 | 群组逻辑删除，所有成员逻辑删除 |
| `dismissGroup_shouldThrow_whenNotOwner` | 非群主尝试解散 | 抛出 NO_PERMISSION |
| `setAdmin_shouldSucceed` | 群主将成员设为管理员 | 目标角色变为 ADMIN |
| `removeAdmin_shouldSucceed` | 群主取消管理员 | 目标角色变为 MEMBER |
| `setAdmin_shouldThrow_whenNotOwner` | 非群主设置管理员 | 抛出 NO_PERMISSION |
| `setAdmin_shouldThrow_whenTargetNotMember` | 目标不在群中 | 抛出 NOT_FOUND |
| `setNickname_shouldSucceed` | 成员设置群昵称 | nickname 字段更新 |
| `setNickname_shouldThrow_whenNotMember` | 非成员设置昵称 | 抛出 NO_PERMISSION |

### 7.2 GroupController 新增测试用例

| 测试方法 | 端点 | 验证点 |
|---------|------|--------|
| `transferOwner_shouldReturnOk` | POST `/api/group/{groupId}/transfer` | 200 + 正确调用 Service |
| `transferOwner_shouldReturnBadRequest` | 参数校验失败 | 400 |
| `dismissGroup_shouldReturnOk` | DELETE `/api/group/{groupId}` | 200 + 正确调用 Service |
| `setAdmin_shouldReturnOk` | POST `/api/group/{groupId}/admin` | 200 + 正确调用 Service |
| `setAdmin_shouldReturnBadRequest` | action 为空 | 400 |
| `setNickname_shouldReturnOk` | PUT `/api/group/{groupId}/nickname` | 200 + 正确调用 Service |

---

## 八、注意事项

1. **接口一致性**: 所有新增接口遵循现有 `ApiResult` 响应格式和分页规范
2. **权限校验**: 在 Service 层统一校验，Controller 层不处理权限逻辑
3. **事务管理**: 转让群主、解散群组涉及多表操作，必须使用 `@Transactional`
4. **日志记录**: 转让、解散、设管理员等关键操作必须记录操作人和操作对象
5. **WebSocket 推送**: 操作成功后再推送，失败不回滚已推送的消息（最终一致性）
6. **文件行数限制**: `GroupService.java` 当前401行，新增4个方法可能超过400行限制，需关注。如果超限，可将群管理相关方法拆分到新类 `