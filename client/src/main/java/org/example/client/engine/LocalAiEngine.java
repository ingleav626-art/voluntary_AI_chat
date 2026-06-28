package org.example.client.engine;

import com.voluntary.chat.server.client.OpenAiClient;
import com.voluntary.chat.server.config.AiConfig;
import com.voluntary.chat.server.entity.AiMemory;
import com.voluntary.chat.server.entity.AiProfile;
import com.voluntary.chat.server.entity.Message;
import com.voluntary.chat.server.util.AesKeyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 本地 AI 引擎门面（无 Spring 依赖）
 *
 * <p>
 * 客户包通过此类调用所有 AI 功能，替代 HTTP/WebSocket 回环。
 * 懒加载初始化，首次 AI 对话时自动初始化 H2 连接和 AI 引擎组件。
 * </p>
 *
 * <p>
 * 支持操作：
 * </p>
 * <ul>
 * <li>AI 角色管理：列表、创建、修改、删除</li>
 * <li>AI 对话：流式对话，直接调用 AI 提供商 API</li>
 * <li>AI 记忆：查看记忆列表</li>
 * </ul>
 */
public class LocalAiEngine implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(LocalAiEngine.class);

    private static volatile LocalAiEngine instance;

    /** AI 配置（POJO 版，无 Spring 注解） */
    private AiConfig aiConfig;

    /** OpenAI 兼容客户端（纯 HTTP，无 Spring 依赖） */
    private OpenAiClient openAiClient;

    /** JDBC 数据访问层（直连 H2） */
    private JdbcAiProfileRepository repository;

    /** 本地用户认证（H2 密码登录兜底） */
    private JdbcUserRepository userRepository;

    /** AI 对话异步线程池 */
    private ExecutorService executor;

    /** 加密密钥 */
    private String encryptionKey;

    /** 是否已初始化 */
    private volatile boolean initialized;

    /** H2 数据库路径 */
    private String h2Path;

    private LocalAiEngine() {
        // 懒加载，构造时不初始化
    }

    /**
     * 获取单例实例
     */
    public static LocalAiEngine getInstance() {
        if (instance == null) {
            synchronized (LocalAiEngine.class) {
                if (instance == null) {
                    instance = new LocalAiEngine();
                }
            }
        }
        return instance;
    }

    /**
     * 初始化本地 AI 引擎
     *
     * <p>
     * 建立 H2 数据库连接，初始化 AI 配置和 AI 客户端。
     * 此方法耗时约 200ms，建议在首次需要 AI 功能时调用。
     * </p>
     */
    public void initialize() {
        if (initialized) {
            LOG.debug("LocalAiEngine 已初始化，跳过");
            return;
        }
        LOG.info("正在初始化本地 AI 引擎...");
        long start = System.currentTimeMillis();

        try {
            // 1. 确定 H2 数据目录
            String dataDir = System.getProperty("app.data.dir");
            if (dataDir == null) {
                String appdata = System.getenv("APPDATA");
                dataDir = appdata != null ? appdata + "/Voluntary-AI-Chat/data" : "./data";
            }
            Path dir = Path.of(dataDir);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            this.h2Path = dir.toAbsolutePath().toString();

            // 2. 初始化 JDBC 数据访问层
            this.repository = new JdbcAiProfileRepository(h2Path);

            // 使用同一 H2 连接的 user repo
            this.userRepository = new JdbcUserRepository(repository.getConnection());

            // 3. 初始化 AI 配置（POJO）
            this.aiConfig = new AiConfig();
            // 从环境变量或系统属性读取加密密钥
            this.encryptionKey = System.getProperty("ai.encryption-key",
                    System.getenv("AI_ENCRYPTION_KEY"));
            if (encryptionKey != null && !encryptionKey.isEmpty()) {
                aiConfig.setEncryptionKey(encryptionKey);
            }
            aiConfig.setDefaultTemperature(0.7);
            aiConfig.setDefaultMaxTokens(2048);
            // 上下文配置
            AiConfig.ContextConfig contextConfig = new AiConfig.ContextConfig();
            contextConfig.setMaxHistoryRounds(10);
            contextConfig.setMaxMemoryCount(3);
            aiConfig.setContext(contextConfig);
            // 记忆配置
            AiConfig.MemoryConfig memoryConfig = new AiConfig.MemoryConfig();
            memoryConfig.setSummarizeThreshold(20);
            memoryConfig.setMaxSummaryLength(500);
            aiConfig.setMemory(memoryConfig);

            // 4. 初始化 OpenAI 客户端（手动注入 AI 配置）
            this.openAiClient = new OpenAiClient(aiConfig);

            // 5. 初始化线程池
            this.executor = Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "local-ai-engine");
                t.setDaemon(true);
                return t;
            });

            this.initialized = true;
            long elapsed = System.currentTimeMillis() - start;
            LOG.info("本地 AI 引擎初始化完成，耗时 {}ms", elapsed);

        } catch (Exception e) {
            LOG.error("本地 AI 引擎初始化失败", e);
            throw new RuntimeException("本地 AI 引擎初始化失败", e);
        }
    }

    // ==================== AI 角色管理 ====================

    // ==================== 本地用户认证（云端不可用时兜底） ====================

    /**
     * 本地登录（H2 直连，PBKDF2 密码验证）
     *
     * <p>
     * 仅在云端服务器不可用时调用。成功时返回模拟的 LoginResponse，
     * 包含本地生成的基础 Token（不用于云端校验，仅用于本地会话标识）。
     * </p>
     *
     * @param phone    手机号
     * @param password 明文密码
     * @return 本地用户信息，验证失败返回 null
     */
    public JdbcUserRepository.LocalUser loginLocal(String phone, String password) {
        ensureInitialized();
        return userRepository.login(phone, password);
    }

    /**
     * 本地注册（H2 直连，无需短信验证码）
     *
     * <p>
     * 仅在云端服务器不可用时调用。
     * </p>
     *
     * @param phone    手机号
     * @param username 用户名
     * @param password 明文密码
     * @return 注册成功的用户 ID，失败返回 null
     */
    public Long registerLocal(String phone, String username, String password) {
        ensureInitialized();
        return userRepository.register(phone, username, password);
    }

    /**
     * 检查本地用户是否存在
     */
    public boolean localUserExists(String phone) {
        ensureInitialized();
        return userRepository.userExists(phone);
    }

    // ==================== AI 角色管理 ====================

    /**
     * 获取 AI 角色列表
     */
    public List<AiProfile> listAiProfiles(Long userId) {
        ensureInitialized();
        return repository.listAiProfiles(userId);
    }

    /**
     * 创建 AI 角色
     *
     * @param userId        用户ID
     * @param name          AI 名称
     * @param persona       AI 人设
     * @param systemPrompt  系统提示词
     * @param modelProvider 模型提供商
     * @param model         模型名称
     * @param apiKey        API Key（明文，入库前自动加密）
     * @param temperature   温度参数
     * @param maxTokens     最大 Token 数
     * @return 创建的 AI 角色 ID
     */
    public Long createAiProfile(Long userId, String name, String avatar, String persona,
            String systemPrompt, String modelProvider, String model,
            String apiKey, Boolean isGroup, Double temperature, Integer maxTokens) {
        ensureInitialized();

        AiProfile profile = new AiProfile();
        profile.setId(generateId());
        profile.setUserId(userId);
        profile.setName(name);
        profile.setAvatar(avatar);
        profile.setPersona(persona);
        profile.setSystemPrompt(systemPrompt);
        profile.setModelProvider(modelProvider);
        profile.setModel(model);
        // 加密 API Key
        String encKey = getEncryptionKey();
        profile.setApiKeyEnc(AesKeyUtil.encrypt(apiKey, encKey));
        profile.setIsGroup(isGroup != null ? isGroup : false);
        profile.setTemperature(temperature != null ? temperature : aiConfig.getDefaultTemperature());
        profile.setMaxTokens(maxTokens != null ? maxTokens : aiConfig.getDefaultMaxTokens());
        profile.setStatus(0);

        repository.insertAiProfile(profile);
        LOG.info("AI 角色创建成功: aiId={}, name={}", profile.getId(), name);
        return profile.getId();
    }

    /**
     * 更新 AI 角色
     */
    public void updateAiProfile(Long aiId, Long userId, String name, String avatar,
            String persona, String systemPrompt, String model,
            String apiKey, Boolean isGroup, Double temperature, Integer maxTokens) {
        ensureInitialized();

        AiProfile profile = repository.findAiProfileById(aiId);
        if (profile == null) {
            throw new RuntimeException("AI 角色不存在: " + aiId);
        }
        // 简单权限校验：仅允许所有者修改
        if (!profile.getUserId().equals(userId)) {
            throw new RuntimeException("无权修改此 AI 角色");
        }

        if (name != null)
            profile.setName(name);
        if (avatar != null)
            profile.setAvatar(avatar);
        if (persona != null)
            profile.setPersona(persona);
        if (systemPrompt != null)
            profile.setSystemPrompt(systemPrompt);
        if (model != null)
            profile.setModel(model);
        if (apiKey != null) {
            profile.setApiKeyEnc(AesKeyUtil.encrypt(apiKey, getEncryptionKey()));
        }
        if (isGroup != null)
            profile.setIsGroup(isGroup);
        if (temperature != null)
            profile.setTemperature(temperature);
        if (maxTokens != null)
            profile.setMaxTokens(maxTokens);

        repository.updateAiProfile(profile);
        LOG.info("AI 角色更新成功: aiId={}", aiId);
    }

    /**
     * 删除 AI 角色
     */
    public void deleteAiProfile(Long aiId, Long userId) {
        ensureInitialized();

        AiProfile profile = repository.findAiProfileById(aiId);
        if (profile == null) {
            throw new RuntimeException("AI 角色不存在: " + aiId);
        }
        if (!profile.getUserId().equals(userId)) {
            throw new RuntimeException("无权删除此 AI 角色");
        }
        repository.deleteAiProfile(aiId);
        LOG.info("AI 角色已删除: aiId={}", aiId);
    }

    /**
     * 获取 AI 角色详情
     */
    public AiProfile getAiProfile(Long aiId) {
        ensureInitialized();
        return repository.findAiProfileById(aiId);
    }

    /**
     * 解密 AI 角色的 API Key
     */
    public String decryptApiKey(Long aiId) {
        ensureInitialized();
        AiProfile profile = repository.findAiProfileById(aiId);
        if (profile == null) {
            throw new RuntimeException("AI 角色不存在: " + aiId);
        }
        return AesKeyUtil.decrypt(profile.getApiKeyEnc(), getEncryptionKey());
    }

    // ==================== AI 对话 ====================

    /**
     * AI 流式对话
     *
     * <p>
     * 直接调用 AI 提供商 API，无需 HTTP/WebSocket 回环到内嵌后端。
     * </p>
     *
     * @param aiId     AI 角色 ID
     * @param userId   用户 ID
     * @param content  用户消息内容
     * @param callback 流式回调
     */
    public void chat(Long aiId, Long userId, String content, AiStreamCallback callback) {
        ensureInitialized();
        executor.submit(() -> doChat(aiId, userId, content, callback));
    }

    private void doChat(Long aiId, Long userId, String content, AiStreamCallback callback) {
        try {
            // 1. 获取 AI 角色配置
            AiProfile profile = repository.findAiProfileById(aiId);
            if (profile == null) {
                callback.onError("AI 角色不存在");
                return;
            }

            // 2. 解密 API Key
            String apiKey = AesKeyUtil.decrypt(profile.getApiKeyEnc(), getEncryptionKey());

            // 3. 构建会话 ID
            String sessionId = "a_" + aiId + "_" + userId;

            // 4. 保存用户消息
            Message userMsg = new Message();
            userMsg.setId(generateId());
            userMsg.setSessionId(sessionId);
            userMsg.setSenderId(userId);
            userMsg.setSenderType(0); // USER
            userMsg.setTargetId(aiId);
            userMsg.setTargetType(2); // AI
            userMsg.setType(0);
            userMsg.setContent(content);
            repository.insertMessage(userMsg);

            // 5. 构建对话上下文
            List<Map<String, String>> messages = buildChatContext(profile, userId, sessionId, content);

            // 6. 调用 AI 提供商（流式）
            StringBuilder fullResponse = new StringBuilder();

            // 获取 baseUrl
            String baseUrl = getBaseUrl(profile.getModelProvider());

            OpenAiClient.StreamConfig streamConfig = new OpenAiClient.StreamConfig(
                    baseUrl,
                    apiKey,
                    profile.getModel(),
                    messages,
                    profile.getTemperature(),
                    profile.getMaxTokens(),
                    chunk -> {
                        fullResponse.append(chunk);
                        callback.onChunk(chunk);
                    },
                    completeContent -> {
                        fullResponse.append(completeContent);
                        // 保存 AI 回复消息
                        Message aiMsg = new Message();
                        aiMsg.setId(generateId());
                        aiMsg.setSessionId(sessionId);
                        aiMsg.setSenderId(aiId);
                        aiMsg.setSenderType(1); // AI
                        aiMsg.setTargetId(userId);
                        aiMsg.setTargetType(0); // USER
                        aiMsg.setType(0);
                        aiMsg.setContent(completeContent);
                        repository.insertMessage(aiMsg);

                        callback.onComplete(completeContent, aiMsg.getId());
                    });

            openAiClient.streamChatCompletion(streamConfig);

        } catch (Exception e) {
            LOG.error("AI 对话失败: aiId={}, userId={}", aiId, userId, e);
            callback.onError("AI 回复失败: " + e.getMessage());
        }
    }

    /**
     * 构建对话上下文（含系统提示词、记忆、历史消息）
     */
    private List<Map<String, String>> buildChatContext(AiProfile profile, Long userId,
            String sessionId, String userContent) {
        List<Map<String, String>> messages = new ArrayList<>();

        // 系统提示词
        String systemPrompt = profile.getSystemPrompt() != null
                ? profile.getSystemPrompt()
                : profile.getPersona();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            Map<String, String> sysMsg = new HashMap<>();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt);
            messages.add(sysMsg);
        }

        // 历史记忆（简化版，仅取最近的记忆）
        // （完整版需要结合向量检索，这里保持简单）
        List<AiMemory> memories = repository.findMemories(profile.getId(), userId, 3);
        if (!memories.isEmpty()) {
            StringBuilder memoryCtx = new StringBuilder("以下是关于该用户的长期记忆，请参考：\n");
            for (int i = 0; i < memories.size(); i++) {
                memoryCtx.append(i + 1).append(". ").append(memories.get(i).getSummary()).append("\n");
            }
            Map<String, String> memMsg = new HashMap<>();
            memMsg.put("role", "system");
            memMsg.put("content", memoryCtx.toString());
            messages.add(memMsg);
        }

        // 历史消息
        int maxHistoryRounds = aiConfig.getContext().getMaxHistoryRounds();
        List<Message> history = repository.findMessages(sessionId, maxHistoryRounds * 2);
        for (Message msg : history) {
            Map<String, String> histMsg = new HashMap<>();
            histMsg.put("role", msg.getSenderType() == 1 ? "assistant" : "user");
            histMsg.put("content", msg.getContent());
            messages.add(histMsg);
        }

        // 当前用户消息
        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userContent);
        messages.add(userMsg);

        return messages;
    }

    // ==================== AI 记忆 ====================

    /**
     * 获取 AI 记忆列表
     */
    public List<AiMemory> listMemories(Long aiId, Long userId) {
        ensureInitialized();
        return repository.findMemories(aiId, userId, 50);
    }

    // ==================== 内部方法 ====================

    private void ensureInitialized() {
        if (!initialized) {
            initialize();
        }
    }

    private String getEncryptionKey() {
        if (encryptionKey == null || encryptionKey.isEmpty()) {
            LOG.error("AI 加密密钥未配置，请设置 ai.encryption-key 或环境变量 AI_ENCRYPTION_KEY");
            throw new RuntimeException("AI 加密密钥未配置");
        }
        return encryptionKey;
    }

    /**
     * 获取模型提供商的 API Base URL
     */
    private String getBaseUrl(String modelProvider) {
        if (modelProvider == null)
            return "https://api.openai.com/v1";

        Map<String, String> defaultUrls = new HashMap<>();
        defaultUrls.put("openai", "https://api.openai.com/v1");
        defaultUrls.put("deepseek", "https://api.deepseek.com/v1");
        defaultUrls.put("qwen", "https://dashscope.aliyuncs.com/compatible-mode/v1");
        defaultUrls.put("zhipu", "https://open.bigmodel.cn/api/paas/v4");

        return defaultUrls.getOrDefault(modelProvider, "https://api.openai.com/v1");
    }

    /**
     * 生成雪花算法风格 ID（简化版）
     */
    private long generateId() {
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
    }

    // ==================== 关闭 ====================

    /**
     * 关闭引擎，释放资源
     */
    @Override
    public void close() {
        LOG.info("正在关闭本地 AI 引擎...");
        if (executor != null) {
            executor.shutdown();
        }
        if (repository != null) {
            repository.close();
        }
        initialized = false;
        instance = null;
        LOG.info("本地 AI 引擎已关闭");
    }

    /**
     * 是否已初始化
     */
    public boolean isInitialized() {
        return initialized;
    }
}