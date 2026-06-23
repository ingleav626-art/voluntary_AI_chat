# 错误日志

## Terminal#382-435 错误分析

### 错误现象

在未登录状态下启动客户端时，终端出现以下错误：

```
No static resource api/group/list
No static resource api/group/create
```

### 错误原因

#### 1. 认证机制分析

根据代码分析，群组接口 `/api/group/**` 需要JWT认证：

**服务端配置：**
- [SecurityConfig.java](file:///c:/Users/OTC/Desktop/demofu/demo/server/src/main/java/com/voluntary/chat/server/config/SecurityConfig.java) 配置了公开URL：
  ```java
  private static final String[] PUBLIC_URLS = {
      "/api/auth/**",  // 认证接口公开
      "/ws/**",        // WebSocket公开
      "/actuator/**"   // 监控接口公开
  };
  ```
  其他所有接口都需要认证（`.anyRequest().authenticated()`）

- [GroupController.java](file:///c:/Users/OTC/Desktop/demofu/demo/server/src/main/java/com/voluntary/chat/server/controller/GroupController.java) 的所有接口都调用了 `SecurityUtils.getCurrentUserId()`，需要认证用户

**认证流程：**
1. [JwtAuthenticationFilter.java](file:///c:/Users/OTC/Desktop/demofu/demo/server/src/main/java/com/voluntary/chat/server/security/JwtAuthenticationFilter.java) 拦截所有请求
2. 从请求头 `Authorization: Bearer {token}` 或参数 `token` 中提取JWT
3. 验证Token有效性
4. 如果Token有效，设置 `SecurityContext` 认证信息
5. 如果Token无效或不存在，`SecurityContext` 为空

#### 2. 客户端调用流程

[GroupService.java](file:///c:/Users/OTC/Desktop/demofu/demo/client/src/main/java/org/example/client/service/GroupService.java) 在调用群组接口时：

```java
public CompletableFuture<ApiResponse<PageResult<GroupInfo>>> getGroupList(final int page, final int size) {
    final String path = GROUP_PATH + "/list?page=" + page + "&size=" + size;
    final HttpRequest request = buildGetRequest(path).build();
    // ...
}
```

`buildGetRequest` 方法会自动添加存储在 `TokenStorage` 中的JWT Token。

#### 3. 错误触发场景

当用户未登录时：
1. `TokenStorage` 中没有有效的JWT Token
2. 客户端发起 `/api/group/list` 和 `/api/group/create` 请求
3. 请求到达服务端，`JwtAuthenticationFilter` 没有找到Token
4. `SecurityContext` 为空，用户未认证
5. Spring Security 拦截请求，返回 "No static resource api/group/list" 错误

### 解决方案

#### 方案一：客户端添加登录状态检查（推荐）

在调用需要认证的接口前，检查用户登录状态：

```java
// GroupService.java 或 GroupListViewModel.java
public CompletableFuture<ApiResponse<PageResult<GroupInfo>>> getGroupList(final int page, final int size) {
    // 检查登录状态
    if (!AuthService.getInstance().isLoggedIn()) {
        LOG.warn("用户未登录，无法获取群组列表");
        return CompletableFuture.completedFuture(
            ApiResponse.error(401, "请先登录")
        );
    }

    final String path = GROUP_PATH + "/list?page=" + page + "&size=" + size;
    final HttpRequest request = buildGetRequest(path).build();
    // ...
}
```

#### 方案二：服务端优化错误响应

在 [GlobalExceptionHandler.java](file:///c:/Users/OTC/Desktop/demofu/demo/server/src/main/java/com/voluntary/chat/server/common/GlobalExceptionHandler.java) 中添加认证异常处理：

```java
@ExceptionHandler(AccessDeniedException.class)
@ResponseStatus(HttpStatus.FORBIDDEN)
public ApiResult<Void> handleAccessDeniedException(AccessDeniedException e) {
    log.warn("访问被拒绝: {}", e.getMessage());
    return ApiResult.error(ErrorCode.FORBIDDEN.getCode(), "权限不足");
}

@ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public ApiResult<Void> handleAuthenticationException(AuthenticationCredentialsNotFoundException e) {
    log.warn("认证失败: {}", e.getMessage());
    return ApiResult.error(ErrorCode.UNAUTHORIZED.getCode(), "请先登录");
}
```

#### 方案三：客户端统一错误处理

在 [BaseHttpService.java](file:///c:/Users/OTC/Desktop/demofu/demo/client/src/main/java/org/example/client/service/BaseHttpService.java) 中添加统一的401错误处理：

```java
protected <T> CompletableFuture<ApiResponse<T>> sendRequest(HttpRequest request, JavaType responseType) {
    return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
        .thenApply(response -> {
            if (response.statusCode() == 401) {
                // 跳转到登录页面
                Platform.runLater(() -> {
                    TokenStorage.clearToken();
                    // 切换到登录界面
                });
                return ApiResponse.error(401, "登录已过期，请重新登录");
            }
            // ... 其他处理
        });
}
```

### 最佳实践建议

1. **客户端防护**：在调用需要认证的接口前，先检查登录状态
2. **服务端友好提示**：优化认证失败的错误信息，返回明确的JSON格式错误
3. **统一错误处理**：在BaseHttpService中统一处理401错误，自动跳转登录页
4. **日志记录**：在客户端和服务端都记录认证失败的日志，便于排查问题

### 相关文件

- [SecurityConfig.java](file:///c:/Users/OTC/Desktop/demofu/demo/server/src/main/java/com/voluntary/chat/server/config/SecurityConfig.java) - 安全配置
- [JwtAuthenticationFilter.java](file:///c:/Users/OTC/Desktop/demofu/demo/server/src/main/java/com/voluntary/chat/server/security/JwtAuthenticationFilter.java) - JWT认证过滤器
- [GroupController.java](file:///c:/Users/OTC/Desktop/demofu/demo/server/src/main/java/com/voluntary/chat/server/controller/GroupController.java) - 群组控制器
- [GroupService.java](file:///c:/Users/OTC/Desktop/demofu/demo/client/src/main/java/org/example/client/service/GroupService.java) - 群组服务客户端
- [GlobalExceptionHandler.java](file:///c:/Users/OTC/Desktop/demofu/demo/server/src/main/java/com/voluntary/chat/server/common/GlobalExceptionHandler.java) - 全局异常处理器

### 总结

该错误是由于客户端在未登录状态下访问需要JWT认证的群组接口导致的。建议采用**方案一**在客户端添加登录状态检查，同时配合**方案三**统一处理401错误，提供更好的用户体验。

---

## 修复实施记录

### 修复时间
2026-06-22 17:09

### 实施方案
采用**方案一**（客户端添加登录状态检查）进行修复。

### 修复内容

#### 1. AuthService 新增登录状态检查方法
在 [AuthService.java](file:///c:/Users/OTC/Desktop/demofu/demo/client/src/main/java/org/example/client/service/AuthService.java) 中添加：

```java
/**
 * 检查用户是否已登录
 *
 * <p>通过检查 TokenStorage 中是否存在有效的 Token 来判断登录状态。</p>
 *
 * @return true 如果已登录，false 如果未登录
 */
public boolean isLoggedIn() {
    final org.example.client.model.LoginResponse token = TokenStorage.load();
    return token != null && token.getAccessToken() != null;
}
```

#### 2. BaseHttpService 新增登录状态检查工具方法
在 [BaseHttpService.java](file:///c:/Users/OTC/Desktop/demofu/demo/client/src/main/java/org/example/client/service/BaseHttpService.java) 中添加：

```java
/**
 * 检查登录状态
 *
 * <p>在调用需要认证的接口前，先检查用户是否已登录。</p>
 *
 * @return true 如果已登录，false 如果未登录
 */
protected boolean checkLoginStatus() {
    final org.example.client.model.LoginResponse token = TokenStorage.load();
    return token != null && token.getAccessToken() != null;
}

/**
 * 创建未登录错误响应
 *
 * @param <T> 响应数据泛型
 * @return API 响应
 */
protected <T> ApiResponse<T> createNotLoggedInResponse() {
    LOG.warn("用户未登录，无法访问需要认证的接口");
    return createErrorResponse(HTTP_UNAUTHORIZED, "请先登录");
}
```

#### 3. GroupService 调用登录状态检查
在 [GroupService.java](file:///c:/Users/OTC/Desktop/demofu/demo/client/src/main/java/org/example/client/service/GroupService.java) 的 `getGroupList` 和 `createGroup` 方法中添加登录状态检查：

```java
public CompletableFuture<ApiResponse<PageResult<GroupInfo>>> getGroupList(final int page, final int size) {
    // 检查登录状态
    if (!checkLoginStatus()) {
        LOG.warn("用户未登录，无法获取群组列表");
        return CompletableFuture.completedFuture(createNotLoggedInResponse());
    }
    // ... 原有逻辑
}

public CompletableFuture<ApiResponse<CreateGroupResponse>> createGroup(final CreateGroupRequest request) {
    // 检查登录状态
    if (!checkLoginStatus()) {
        LOG.warn("用户未登录，无法创建群组");
        return CompletableFuture.completedFuture(createNotLoggedInResponse());
    }
    // ... 原有逻辑
}
```

### 测试结果
- 编译测试：✅ 成功
- AuthServiceTest：✅ 通过（5个测试全部通过）

### 效果验证
修复后，未登录用户访问群组接口时：
1. 客户端在发送请求前检查登录状态
2. 如果未登录，直接返回错误响应，不发送请求到服务端
3. 避免了服务端返回 "No static resource" 错误
4. 用户收到明确的错误提示："请先登录"

### 后续优化建议
1. 在其他需要认证的服务类（如 FriendService、UserService）中也添加登录状态检查
2. 在 ViewModel 中处理未登录错误响应，提示用户跳转到登录页
3. 实施方案三：在 BaseHttpService 中统一处理401错误，自动跳转登录页