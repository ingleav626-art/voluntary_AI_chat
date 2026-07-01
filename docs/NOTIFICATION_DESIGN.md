# 消息通知系统设计文档

> 为项目补全缺失的消息通知能力，让用户在窗口最小化时也能及时收到关键信息。

---

## 现状调研

| 已有能力 | 位置 | 说明 |
|----------|------|------|
| 系统托盘框架 | [App.java](file:///d:/voluntary_ai_chat/client/src/main/java/org/example/client/App.java#L451) | AWT TrayIcon，支持左键唤出 + 右键菜单（显示窗口/退出） |
| 消息弹窗 | [NotificationDialog.java](file:///d:/voluntary_ai_chat/client/src/main/java/org/example/client/controller/NotificationDialog.java) | JavaFX 模态弹窗，SUCCESS/INFO/WARNING/ERROR 四种类型 |
| 未读徽章 | [MainController.java](file:///d:/voluntary_ai_chat/client/src/main/java/org/example/client/controller/MainController.java#L631) | 会话列表未读计数红色徽章 |
| 已读上报 | [ChatViewModel.java](file:///d:/voluntary_ai_chat/client/src/main/java/org/example/client/view/ChatViewModel.java#L585) | 切换会话时上报已读消息 |

**缺失的部分**：
- ❌ 收到新消息时无 **系统托盘气泡提示**（balloon notification）
- ❌ 收到新消息时无 **桌面通知**（Windows Toast / 系统通知中心）
- ❌ AI 主动问候/追问无通知提醒
- ❌ 断线重连状态无用户可见通知
- ❌ 待办系统提醒无实现基础
- ❌ 无统一的通知管理入口

---

## 设计目标

1. **窗口外通知** — 窗口最小化/在后台时，新消息能通知用户
2. **按需通知** — 用户可控哪些场景需要通知
3. **不打扰** — 非当前会话的消息才通知，当前会话内不重复打扰
4. **统一入口** — 所有通知设置集中管理

---

## 系统架构

```
┌──────────────────────────────────────────────────────────┐
│                    NotificationManager                     │
│  (统一通知管理，单例，可被各 ViewModel 调用)                │
│                                                           │
│  ┌────────────────┐  ┌──────────────┐  ┌───────────────┐  │
│  │ TrayNotifier   │  │ ToastNotifier │  │ SoundNotifier │  │
│  │ 系统托盘气泡    │  │ 系统Toast通知 │  │ 声音提醒      │  │
│  └───────┬────────┘  └──────┬───────┘  └───────┬───────┘  │
│          │                  │                   │          │
│          ▼                  ▼                   ▼          │
│  ┌──────────────────────────────────────────────────────┐  │
│  │                  通知队列                               │  │
│  │  防抖合并：同一会话 5 秒内多条消息合并为一条通知         │  │
│  └──────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────┘
           │
           ▼
┌──────────────────────┐     ┌──────────────────────────┐
│  通知场景判定          │     │  通知设置 (用户偏好)       │
│  · 窗口是否最小化      │     │  · 新消息通知 开/关       │
│  · 消息所属会话是否  │     │  · AI 主动问候 开/关      │
│    当前查看的会话      │     │  · 待办提醒 开/关         │
│  · 用户是否在线        │     │  · 声音通知 开/关        │
│  · 场景是否开启通知    │     │  · 免打扰时段            │
└──────────────────────┘     └──────────────────────────┘
```

---

## 通知通道

### 1. 系统托盘气泡通知（TrayIcon Balloon）

**基于现有 TrayIcon 扩展**，AWT TrayIcon 原生支持 `displayMessage()`。

| 能力 | 说明 |
|------|------|
| 展示位置 | Windows 系统托盘区域气泡 |
| 自动消失 | 操作系统控制，默认约 5-8s |
| 点击行为 | 点击气泡 → 唤出窗口并跳转到对应会话 |
| 限制 | Windows 7+/Linux 均支持，macOS 不支持 |
| 优先级 | 关键通知必须走此通道 |

**接口设计**：
```java
public class TrayNotifier {
    /**
     * 显示系统托盘气泡通知
     *
     * @param title   标题（如"新消息"）
     * @param message 内容（如"张三：你好啊"）
     * @param type    类型（INFO/WARNING/ERROR）
     * @param sessionId 可选，点击后跳转到对应会话
     */
    public static void showNotification(
        String title, String message, 
        NotificationType type, String sessionId
    ) {
        if (!trayEnabled || trayIcon == null) return;
        trayIcon.displayMessage(title, message, 
            type.toTrayIconMessageType());
        // 存储 sessionId，点击气泡时跳转
        pendingNotificationSession = sessionId;
    }
}
```

**点击气泡处理**（当前 `trayIcon.addMouseListener` 只能区分左键/右键，**无法单独捕获气泡点击**）。解决方案：

```
方案 A（推荐）：点击气泡后气泡消失，用户只能通过左键单击托盘图标
  左键单击 → 唤出窗口 + 打开 pendingNotificationSession
  限制：无法精确区分"用户主动想看"和"通知后想看"

方案 B：升级到 Java 9+ Notification API
  使用 java.awt.TrayIcon.displayMessage() 后，监听 mouseClicked
  配合时间来近似判断：displayMessage 后 15s 内的左键单击视为通知点击
```

### 2. 系统 Toast 通知（Windows Toast / Java AWT Notification）

**为什么需要**：TrayIcon 气泡在 Windows 11 上可能被系统通知中心替代，需要一个备选通道。

| 方案 | 优点 | 缺点 |
|------|------|------|
| **Java AWT Notification (displayMessage)** | 已有 TrayIcon，零依赖 | 自定义程度低 |
| **Windows Toast + JNI / ProcessBuilder** | 原生 Windows 通知 | 跨平台差 |
| **JavaFX 自定义 Stage 弹窗** | 跨平台，UI 可控 | 不是系统通知 |

**推荐实现**：现阶段优先使用 AWT TrayIcon `displayMessage()`，后续项目成熟后接入 [Java 22+ SystemTray enhancements](https://openjdk.org/jeps/461) 或 Windows Toast SDK。

### 3. 应用内通知横幅（In-App Banner）

**定位**：窗口在前台但不一定在看聊天时，从顶部滑入的横幅通知。

```
┌──────────────────────────────────────────────┐
│  ┌──────────────────────────────────────┐    │
│  │ 📩 张三：明天下午3点开会别忘了～    [×] │    │  ← 顶部横幅
│  └──────────────────────────────────────┘    │
│                                              │
│  [会话列表]         [聊天区域]                │
│  ┌────────┐        ┌──────────────────┐      │
│  │ 张三  3│        │ 你好             │      │
│  │ 李四   │        │ 在吗？           │      │
│  │ 群聊   │        │                  │      │
│  └────────┘        └──────────────────┘      │
└──────────────────────────────────────────────┘
```

| 能力 | 说明 |
|------|------|
| 展示位置 | 主窗口顶部，覆盖在内容上方 |
| 样式 | 半透明磨砂背景，5s 后自动消失 |
| 交互 | 点击横幅 → 跳到对应会话 |
| 关闭 | 点击 × / 5s 超时 / 手动关闭 |

### 4. 声音提醒

| 场景 | 声音 | 说明 |
|------|------|------|
| 收到新消息 | 短促提示音（~200ms） | 默认开启 |
| AI 主动问候 | 柔和提示音（~500ms） | 可单独关闭 |
| 待办提醒 | 持续提示音（~1s） | 不可关闭 |
| 断线重连 | 无声音 | 通过状态栏提示 |

**技术方案**：
- JavaFX 内置 `MediaPlayer` 播放 `audio/` 目录下的 WAV 文件
- 文件极小（每个 <50KB），打包在 JAR 中
- 支持用户自定义提示音（替换文件）

---

## 通知场景

### 场景一：收到新消息

**触发条件**：
1. 收到 `RECEIVE_MESSAGE` 或 `GROUP_MESSAGE` 类型的 WebSocket 消息
2. 消息发送者不是当前用户自己
3. （可选）消息所属会话不是当前正在查看的会话

**通知流程**：
```
收到 WebSocket 消息
  → 判断通知条件
  → 窗口是否最小化/在后台？
    是 → 系统托盘气泡通知
    否 → 判断是否当前会话？
        是 → 不通知（UI 直接显示）
        否 → 应用内横幅通知
  → 如果开启了声音，播放提示音
  → 更新未读徽章
```

**消息防抖**：同一会话 5 秒内的多条消息合并为一条通知
```
"张三：你好" + "张三：在吗？" + "张三：看到回复"
  → 合并为 "张三：你好 及 2 条消息"
```

### 场景二：AI 主动问候/追问

**触发条件**：
1. AI 主动发送了问候消息（`AI_GREETING` / `AI_CHAT` 类型）
2. AI 主动追问（用户在沉默状态）
3. AI 主动聊天功能已开启

**通知行为**：
```
AI 主动问候
  → 判断通知条件
  → 系统托盘气泡："🤖 小助手：早上好～"
  → 应用内横幅：显示在顶部
  → 柔和提示音
```

**防打扰**：AI 主动问候通知受"免打扰时段"和"每日上限"控制。

### 场景三：待办提醒

**触发条件**：待办设定的时间到达

**通知流程**：
```
待办时间到
  → 系统托盘气泡："⏰ 提醒：15:00的会议时间到了"
  → 应用内横幅："15:00的会议，别忘了～"
  → 持续提示音（1s）
  → AI 在聊天中也发送一条提醒消息
```

**不可忽略**：待办提醒会持续通知，直到用户手动标记完成或推迟。

### 场景四：系统事件通知

| 事件 | 通知方式 | 优先级 |
|------|----------|--------|
| 断线 | 状态栏图标变色 + 应用内横幅 | 中 |
| 重连成功 | 状态栏图标恢复 + 应用内横幅（短暂） | 低 |
| 强制下线 | 系统托盘气泡 + 弹窗阻断 | 最高 |
| 好友申请 | 系统托盘气泡 + 未读徽章 | 中 |
| 群事件 | 更新列表（不推送通知） | 低 |

### 场景五：打招呼提醒（后续）

**定位**：用户长时间未使用，AI 无人问津时，给一个温和的提示

```
用户 3 天未打开应用
  → 开启应用时显示：
  "小助手已经 3 天没见到你了，有点想你～"
```

**设计原则**：频率极低（≥72h 未使用），仅展现一次，不推送。

---

## 通知队列与合并策略

```java
public class NotificationQueue {
    // 通知合并：同一会话 5 秒内合并
    private static final long MERGE_WINDOW_MS = 5_000;
    
    // 最大合并条数
    private static final int MAX_MERGE_COUNT = 5;
    
    // 通知限流：每分钟最多 10 条通知
    private static final int MAX_PER_MINUTE = 10;
    
    static class PendingNotification {
        String title;
        StringBuilder message;
        String sessionId;
        int mergeCount;
        long lastUpdated;
    }
}
```

| 策略 | 规则 |
|------|------|
| 合并窗口 | 同一会话 5 秒内多条消息合为一条 |
| 最大合并 | 合并后显示 "张三：你好 及 3 条消息"（最多 5 条）|
| 限流 | 每分钟最多 10 条通知，超出丢弃 |
| 优先级 | 待办 > 系统事件 > 新消息 > AI 问候 |

---

## 通知设置面板（规划）

```
┌──────────────────────────────────────┐
│  🔔 通知设置                         │
│                                      │
│  ─── 消息通知 ───                     │
│  [✓] 新消息通知                      │
│  [✓] 非当前会话时通知                │
│  [✓] 声音提醒                        │
│  通知防抖：5 秒                       │
│                                      │
│  ─── AI 主动聊天 ───                  │
│  [✓] AI 主动问候通知                 │
│  [✓] AI 追问通知                     │
│  通知声音：柔和                         │
│                                      │
│  ─── 待办提醒 ───                     │
│  [✓] 待办提醒（强制通知，不可关闭）     │
│  [✓] 声音提醒                         │
│                                      │
│  ─── 免打扰 ───                       │
│  [✓] 启用免打扰                       │
│  时段：[22:00] - [09:00]              │
│  例外：仅待办提醒                      │
│                                      │
│  ─── 预览 ───                         │
│  [测试通知]                           │
└──────────────────────────────────────┘
```

---

## 通知与现有系统的集成

### 在 App.java 中的集成点

| 现有代码 | 集成方式 |
|----------|----------|
| `hideToTray()` (L512) | 窗口最小化后，通知走托盘气泡 |
| `trayIcon.addMouseListener` (L490) | 左键单击唤出 + 跳转到 pendingNotificationSession |
| `Platform.setImplicitExit(false)` (L161) | 已支持后台运行 |

### 在 MainViewModel.java 中的集成点

```java
// 当前：收到消息只 UI 更新
private void handleReceiveMessage(WebSocketMessage wsMessage) {
    // ... 现有代码 ...
    message.setMessageId(messageId);
    // ++ 新增：触发通知
    NotificationManager.getInstance().notifyNewMessage(message);
}
```

### 在 AiChatService / 主动聊天中的集成点

```java
// AI 主动问候/追问时触发通知
public void sendAiProactiveMessage(...) {
    // ... 现有代码 ...
    // ++ 新增：触发 AI 问候通知
    NotificationManager.getInstance().notifyAiGreeting(aiName, content);
}
```

---

## API 定义（NotificationManager）

```java
/**
 * 统一通知管理器（单例）
 */
public class NotificationManager {
    
    public static NotificationManager getInstance();
    
    // ======= 外部调用入口 =======
    
    /**
     * 新消息通知（由 MainViewModel.handleReceiveMessage 调用）
     */
    public void notifyNewMessage(MessageInfo message);
    
    /**
     * AI 主动问候通知（由主动聊天引擎调用）
     */
    public void notifyAiGreeting(String aiName, String content);
    
    /**
     * 待办提醒（由待办系统调用）
     */
    public void notifyTodoReminder(String title, String content);
    
    /**
     * 系统事件通知
     */
    public void notifySystemEvent(SystemEventType type, String message);
    
    // ======= 内部通知通道 =======
    
    /**
     * 系统托盘气泡 + 点击跳转
     */
    public void showTrayNotification(String title, String message, 
        NotificationType type, String sessionId);
    
    /**
     * 应用内横幅
     */
    public void showInAppBanner(String title, String message, 
        String sessionId);
    
    /**
     * 播放提示音
     */
    public void playSound(SoundType type);
    
    // ======= 通知设置 =======
    
    public boolean isNotificationEnabled(NotificationScene scene);
    public void setNotificationEnabled(NotificationScene scene, boolean enabled);
    public boolean isInDoNotDisturb();
    public Duration getMergeWindow();
}
```

---

## 实施路线

### 阶段一：最小可用通知（P3.5）
- TrayNotifier 封装 `displayMessage()` + 点击跳转
- 新消息通知（系统托盘气泡）
- 通知设置存储 + 基本开关

### 阶段二：应用内横幅（P4）
- In-App Banner UI 组件
- 与 MainController 集成
- 新消息横幅 + AI 问候横幅

### 阶段三：完整通知系统（P4.5）
- 通知队列 + 合并 + 限流
- 声音提醒（WAV 文件 + MediaPlayer）
- 通知设置面板完整 UI

### 阶段四：高级通知（P5）
- 待办提醒集成
- 断线重连状态通知
- 打招呼提醒
- 免打扰时段精确控制

---

## 未解决的问题

1. **TrayIcon 气泡点击**：AWT 无法单独捕获 `displayMessage()` 气泡的点击事件，只能通过左键单击托盘图标唤起。是否有更好的方案？
2. **跨平台通知**：macOS 不原生支持 AWT SystemTray，是否应该为 macOS 单独实现通知通道（AppleScript / JNI）？
3. **通知声音**：用户是否有权上传自定义提示音文件？
4. **通知历史**：是否需要一个通知中心面板查看过往通知？
5. **隐私**：通知内容是否应该预览消息文字？隐私模式下是否屏蔽内容？