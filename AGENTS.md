# AGENTS.md - AI 开发规范（精简版）

> 本文件会每次发送给 AI，请保持简洁。完整规范见 AI_SPEC.md

---

## 项目概述

Java 桌面 AI 聊天软件：JavaFX（客户端）+ Spring Boot 3（服务端）+ WebSocket
远程仓库名称地址：https://github.com/ingleav626-art/voluntary_AI_chat

---

## 🚨 绝对禁止（违反任何一条即失败）

1. **禁止删除任何文件** - 无论文件多么离谱，未获得用户明确同意前，绝对不能删除任何文件
2. **禁止提交敏感信息** - .env、API Key、数据库密码等
3. **禁止硬编码** - 配置、常量必须提取到配置文件或常量类
4. **禁止跳过测试** - 新功能必须有对应测试
5. **禁止直接修改 main 分支** - 必须通过 PR
6. **禁止吞异常** - 必须适当处理或抛出
7. **禁止使用 System.out.println** - 使用 Logger
8. **禁止在循环中执行数据库查询** - 使用批量操作
9. **禁止暴露 API Key** - 必须加密存储
10. **禁止过度抽象** - 不要为假设的扩展性创建复杂设计

---

## ✅ 必须做到

### 代码质量
- 遵循阿里巴巴 Java 开发手册
- 使用 Lombok 减少样板代码
- 复杂逻辑必须添加中文注释说明"为什么"
- 单一职责：每个类只做一件事

### 测试要求
- 工具类覆盖率 ≥ 90%
- Service 覆盖率 ≥ 80%
- Controller 覆盖率 ≥ 75%
- 测试行为而非实现
- Mock 外部依赖

### 文件规范
- Controller ≤ 300 行
- Service ≤ 400 行
- Entity/DTO ≤ 150 行
- 超过限制必须拆分

### Git 提交
```
feat(scope): 描述

为什么做这个改动
具体改了什么
```

### 安全规范
- 用户输入必须验证
- 使用 @Valid 注解
- MyBatis 参数化查询
- JWT + Spring Security 认证

### 性能要求
- JavaFX UI 更新必须用 Platform.runLater()
- 大数据集必须分页
- Redis 缓存热点数据
- 耗时操作异步处理

---

## 技术栈速查

| 模块 | 技术 |
|------|------|
| 客户端 | JavaFX + FXML + WebSocket |
| 服务端 | Spring Boot 3 + MyBatis-Plus |
| 数据库 | MySQL 8 + Redis + Milvus/Qdrant |
| 认证 | Spring Security + JWT |
| 消息队列 | RabbitMQ |
| AI | OpenAI 兼容协议 + RAG |

---

## 常用命令

```bash
mvn test                    # 运行测试
mvn checkstyle:check        # 代码检查
mvn test jacoco:report      # 覆盖率报告
mvn clean package           # 构建项目
```

---

## AI 审查清单（提交前自检）

- [ ] 理解现有代码结构
- [ ] 遵循代码风格
- [ ] 有适当的异常处理
- [ ] 有必要的类型定义
- [ ] 没有重复代码
- [ ] 考虑了性能影响
- [ ] 没有安全漏洞
- [ ] 有对应的测试
- [ ] 更新了相关文档
- [ ] 遵循单一职责原则
