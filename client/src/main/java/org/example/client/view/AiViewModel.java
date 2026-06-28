package org.example.client.view;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.example.client.config.ServerConnectionManager;
import org.example.client.config.ServerMode;
import org.example.client.engine.LocalAiEngine;
import org.example.client.model.AiMemory;
import org.example.client.model.AiProfile;
import org.example.client.model.ApiResponse;
import org.example.client.model.PageResult;
import org.example.client.service.AiService;
import org.example.client.service.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.voluntary.chat.common.constant.MessageTypes;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ListProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;

/**
 * AI 模块视图模型（MVVM）
 *
 * <p>
 * 管理 AI 角色列表、创建/编辑/删除 AI 角色、查看 AI 记忆。
 * </p>
 */
public final class AiViewModel {

    private static final Logger LOG = LoggerFactory.getLogger(AiViewModel.class);

    /** AI 角色列表 */
    private final ListProperty<AiProfile> aiList = new SimpleListProperty<>(
            FXCollections.observableArrayList());

    /** 当前选中的 AI 角色 */
    private final ObjectProperty<AiProfile> selectedAi = new SimpleObjectProperty<>();

    /** AI 记忆列表 */
    private final ListProperty<AiMemory> memories = new SimpleListProperty<>(
            FXCollections.observableArrayList());

    /** 加载状态 */
    private final BooleanProperty loading = new SimpleBooleanProperty(false);

    /** 错误消息 */
    private final StringProperty errorMessage = new SimpleStringProperty("");

    /** 成功消息 */
    private final StringProperty successMessage = new SimpleStringProperty("");

    /** 当前页码 */
    private int currentPage = 1;

    /** 总记录数 */
    private long totalCount = 0;

    /** 每页数量 */
    private static final int PAGE_SIZE = 20;

    /** 模型提供商选项 */
    public static final List<String> MODEL_PROVIDERS = List.of(
            "openai", "deepseek", "qwen", "zhipu", "custom");

    public AiViewModel() {
        // 选中 AI 变化时加载记忆
        selectedAi.addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadMemories(newVal.getAiId());
            } else {
                memories.clear();
            }
        });
    }

    /**
     * 判断当前是否处于本地模式（使用 LocalAiEngine）
     */
    private boolean isLocalMode() {
        return ServerConnectionManager.getInstance().getCurrentMode() == ServerMode.LOCAL;
    }

    /**
     * 获取当前用户 ID
     */
    private Long getCurrentUserId() {
        final org.example.client.util.TokenStorage storage = null;
        final org.example.client.model.LoginResponse login = org.example.client.util.TokenStorage.load();
        if (login != null && login.getUser() != null) {
            return login.getUser().getUserId();
        }
        return 0L;
    }

    /**
     * 将 ai-core 实体转换为客户端模型
     */
    private AiProfile toClientProfile(com.voluntary.chat.server.entity.AiProfile entity) {
        return new AiProfile(
                entity.getId(),
                entity.getName(),
                entity.getAvatar(),
                null, // openingMessage
                entity.getPersona(),
                entity.getModelProvider(),
                entity.getModel(),
                entity.getIsGroup(),
                entity.getSystemPrompt(),
                null, // apiKey (不返回加密密钥)
                entity.getTemperature(),
                entity.getMaxTokens());
    }

    /**
     * 将 ai-core 记忆实体转换为客户端模型
     */
    private AiMemory toClientMemory(com.voluntary.chat.server.entity.AiMemory entity) {
        return new AiMemory(
                entity.getId(),
                entity.getSummary(),
                entity.getKeywords(),
                entity.getImportance(),
                entity.getCreateTime() != null ? entity.getCreateTime().toString() : null);
    }

    /**
     * 加载 AI 角色列表
     */
    public void loadAiList() {
        loading.set(true);
        errorMessage.set("");

        if (isLocalMode()) {
            try {
                final Long userId = getCurrentUserId();
                final List<com.voluntary.chat.server.entity.AiProfile> entities = LocalAiEngine.getInstance()
                        .listAiProfiles(userId);
                final List<AiProfile> list = new ArrayList<>();
                for (com.voluntary.chat.server.entity.AiProfile entity : entities) {
                    list.add(toClientProfile(entity));
                }
                Platform.runLater(() -> {
                    loading.set(false);
                    aiList.setAll(list);
                    totalCount = list.size();
                    LOG.info("AI角色列表加载成功（本地）: count={}", list.size());
                });
            } catch (final Exception ex) {
                LOG.error("加载AI角色列表异常（本地）", ex);
                Platform.runLater(() -> {
                    loading.set(false);
                    errorMessage.set("加载AI列表失败: " + ex.getMessage());
                });
            }
            return;
        }

        AiService.getInstance().getAiList(currentPage, PAGE_SIZE)
                .thenAccept(response -> {
                    Platform.runLater(() -> {
                        loading.set(false);
                        if (response != null && response.isSuccess() && response.getData() != null) {
                            final PageResult<AiProfile> result = response.getData();
                            final List<AiProfile> list = result.getList();
                            aiList.setAll(list != null ? list : new ArrayList<>());
                            totalCount = result.getTotal();
                            LOG.info("AI角色列表加载成功: count={}", aiList.size());
                        } else {
                            final String msg = response != null ? response.getMessage() : "加载AI列表失败";
                            errorMessage.set(msg);
                            LOG.warn("AI角色列表加载失败: {}", msg);
                        }
                    });
                })
                .exceptionally(ex -> {
                    LOG.error("加载AI角色列表异常", ex);
                    Platform.runLater(() -> {
                        loading.set(false);
                        errorMessage.set("网络异常，加载AI列表失败");
                    });
                    return null;
                });
    }

    /**
     * 创建 AI 角色
     *
     * @param profile        AI 角色信息
     * @param openingMessage 开场白消息（创建后自动发送，可为 null）
     */
    public void createAiProfile(final AiProfile profile, final String openingMessage) {
        loading.set(true);
        errorMessage.set("");
        successMessage.set("");

        if (isLocalMode()) {
            try {
                final Long userId = getCurrentUserId();
                final Long aiId = LocalAiEngine.getInstance().createAiProfile(
                        userId,
                        profile.getName(),
                        profile.getAvatar(),
                        profile.getPersona(),
                        profile.getSystemPrompt(),
                        profile.getModelProvider(),
                        profile.getModel(),
                        profile.getApiKey(),
                        profile.getIsGroup(),
                        profile.getTemperature(),
                        profile.getMaxTokens());
                Platform.runLater(() -> {
                    loading.set(false);
                    successMessage.set("AI角色创建成功");
                    LOG.info("AI角色创建成功（本地）: aiId={}", aiId);
                    loadAiList();

                    // 创建成功后自动发送开场白
                    if (openingMessage != null && !openingMessage.trim().isEmpty()) {
                        sendOpeningMessage(aiId, openingMessage.trim());
                    }
                });
            } catch (final Exception ex) {
                LOG.error("创建AI角色异常（本地）", ex);
                Platform.runLater(() -> {
                    loading.set(false);
                    errorMessage.set("创建AI角色失败: " + ex.getMessage());
                });
            }
            return;
        }

        AiService.getInstance().createAiProfile(profile)
                .thenAccept(response -> {
                    Platform.runLater(() -> {
                        loading.set(false);
                        if (response != null && response.isSuccess()) {
                            final Long aiId = response.getData();
                            successMessage.set("AI角色创建成功");
                            LOG.info("AI角色创建成功: aiId={}", aiId);
                            loadAiList();

                            // 创建成功后自动发送开场白
                            if (openingMessage != null && !openingMessage.trim().isEmpty()) {
                                sendOpeningMessage(aiId, openingMessage.trim());
                            }
                        } else {
                            final String msg = response != null ? response.getMessage() : "创建AI角色失败";
                            errorMessage.set(msg);
                            LOG.warn("AI角色创建失败: {}", msg);
                        }
                    });
                })
                .exceptionally(ex -> {
                    LOG.error("创建AI角色异常", ex);
                    Platform.runLater(() -> {
                        loading.set(false);
                        errorMessage.set("网络异常，创建AI角色失败");
                    });
                    return null;
                });
    }

    /**
     * 发送开场白消息到 AI
     *
     * @param aiId    AI 角色ID
     * @param content 消息内容
     */
    private void sendOpeningMessage(final Long aiId, final String content) {
        if (isLocalMode()) {
            // 本地模式：直接使用 LocalAiEngine 发送开场白
            final Long userId = getCurrentUserId();
            LocalAiEngine.getInstance().chat(aiId, userId, content, new org.example.client.engine.AiStreamCallback() {
                @Override
                public void onChunk(String chunk) {
                    // 开场白不需要实时显示
                }

                @Override
                public void onComplete(String fullContent, Long messageId) {
                    LOG.info("[开场白] AI回复完成（本地）: aiId={}, messageId={}", aiId, messageId);
                }

                @Override
                public void onError(String error) {
                    LOG.warn("[开场白] AI回复失败（本地）: aiId={}, error={}", aiId, error);
                }
            });
            return;
        }

        final String sessionId = "a_" + aiId;
        final String messageId = UUID.randomUUID().toString();

        final Map<String, Object> data = new HashMap<>();
        data.put("aiId", aiId);
        data.put("sessionId", sessionId);
        data.put("content", content);

        LOG.info("[开场白] 发送开场白到AI: aiId={}, content={}", aiId, content);
        WebSocketClient.getInstance().send(MessageTypes.AI_CHAT, data);
    }

    /**
     * 修改 AI 角色
     *
     * @param aiId    AI 角色ID
     * @param profile 修改内容
     */
    public void updateAiProfile(final Long aiId, final AiProfile profile) {
        loading.set(true);
        errorMessage.set("");
        successMessage.set("");

        if (isLocalMode()) {
            try {
                final Long userId = getCurrentUserId();
                LocalAiEngine.getInstance().updateAiProfile(
                        aiId, userId,
                        profile.getName(),
                        profile.getAvatar(),
                        profile.getPersona(),
                        profile.getSystemPrompt(),
                        profile.getModel(),
                        profile.getApiKey(),
                        profile.getIsGroup(),
                        profile.getTemperature(),
                        profile.getMaxTokens());
                Platform.runLater(() -> {
                    loading.set(false);
                    successMessage.set("AI角色修改成功");
                    LOG.info("AI角色修改成功（本地）: aiId={}", aiId);
                    loadAiList();
                });
            } catch (final Exception ex) {
                LOG.error("修改AI角色异常（本地）", ex);
                Platform.runLater(() -> {
                    loading.set(false);
                    errorMessage.set("修改AI角色失败: " + ex.getMessage());
                });
            }
            return;
        }

        AiService.getInstance().updateAiProfile(aiId, profile)
                .thenAccept(response -> {
                    Platform.runLater(() -> {
                        loading.set(false);
                        if (response != null && response.isSuccess()) {
                            successMessage.set("AI角色修改成功");
                            LOG.info("AI角色修改成功: aiId={}", aiId);
                            loadAiList();
                        } else {
                            final String msg = response != null ? response.getMessage() : "修改AI角色失败";
                            errorMessage.set(msg);
                            LOG.warn("AI角色修改失败: {}", msg);
                        }
                    });
                })
                .exceptionally(ex -> {
                    LOG.error("修改AI角色异常", ex);
                    Platform.runLater(() -> {
                        loading.set(false);
                        errorMessage.set("网络异常，修改AI角色失败");
                    });
                    return null;
                });
    }

    /**
     * 删除 AI 角色
     *
     * @param aiId AI 角色ID
     */
    public void deleteAiProfile(final Long aiId) {
        loading.set(true);
        errorMessage.set("");
        successMessage.set("");

        if (isLocalMode()) {
            try {
                final Long userId = getCurrentUserId();
                LocalAiEngine.getInstance().deleteAiProfile(aiId, userId);
                Platform.runLater(() -> {
                    loading.set(false);
                    successMessage.set("AI角色已删除");
                    LOG.info("AI角色已删除（本地）: aiId={}", aiId);
                    selectedAi.set(null);
                    loadAiList();
                });
            } catch (final Exception ex) {
                LOG.error("删除AI角色异常（本地）", ex);
                Platform.runLater(() -> {
                    loading.set(false);
                    errorMessage.set("删除AI角色失败: " + ex.getMessage());
                });
            }
            return;
        }

        AiService.getInstance().deleteAiProfile(aiId)
                .thenAccept(response -> {
                    Platform.runLater(() -> {
                        loading.set(false);
                        if (response != null && response.isSuccess()) {
                            successMessage.set("AI角色已删除");
                            LOG.info("AI角色已删除: aiId={}", aiId);
                            selectedAi.set(null);
                            loadAiList();
                        } else {
                            final String msg = response != null ? response.getMessage() : "删除AI角色失败";
                            errorMessage.set(msg);
                            LOG.warn("AI角色删除失败: {}", msg);
                        }
                    });
                })
                .exceptionally(ex -> {
                    LOG.error("删除AI角色异常", ex);
                    Platform.runLater(() -> {
                        loading.set(false);
                        errorMessage.set("网络异常，删除AI角色失败");
                    });
                    return null;
                });
    }

    /**
     * 加载 AI 记忆列表
     *
     * @param aiId AI 角色ID
     */
    public void loadMemories(final Long aiId) {
        if (aiId == null) {
            memories.clear();
            return;
        }

        if (isLocalMode()) {
            try {
                final Long userId = getCurrentUserId();
                final List<com.voluntary.chat.server.entity.AiMemory> entities = LocalAiEngine.getInstance()
                        .listMemories(aiId, userId);
                final List<AiMemory> list = new ArrayList<>();
                for (com.voluntary.chat.server.entity.AiMemory entity : entities) {
                    list.add(toClientMemory(entity));
                }
                Platform.runLater(() -> {
                    memories.setAll(list);
                    LOG.info("AI记忆加载成功（本地）: aiId={}, count={}", aiId, list.size());
                });
            } catch (final Exception ex) {
                LOG.error("加载AI记忆异常（本地）: aiId={}", aiId, ex);
                Platform.runLater(() -> memories.clear());
            }
            return;
        }

        AiService.getInstance().getAiMemories(aiId, 1, 10)
                .thenAccept(response -> {
                    Platform.runLater(() -> {
                        if (response != null && response.isSuccess() && response.getData() != null) {
                            final PageResult<AiMemory> result = response.getData();
                            final List<AiMemory> list = result.getList();
                            memories.setAll(list != null ? list : new ArrayList<>());
                            LOG.info("AI记忆加载成功: aiId={}, count={}", aiId, memories.size());
                        } else {
                            memories.clear();
                            LOG.debug("AI记忆加载失败或为空: aiId={}", aiId);
                        }
                    });
                })
                .exceptionally(ex -> {
                    LOG.error("加载AI记忆异常: aiId={}", aiId, ex);
                    Platform.runLater(() -> memories.clear());
                    return null;
                });
    }

    /**
     * 删除 AI 记忆
     *
     * @param memoryId 记忆ID
     */
    public void deleteMemory(final Long memoryId) {
        final AiProfile selected = selectedAi.get();
        if (selected == null || memoryId == null) {
            return;
        }

        AiService.getInstance().deleteMemory(selected.getAiId(), memoryId)
                .thenAccept(response -> {
                    Platform.runLater(() -> {
                        if (response != null && response.isSuccess()) {
                            successMessage.set("记忆已删除");
                            LOG.info("AI记忆已删除: memoryId={}", memoryId);
                            loadMemories(selected.getAiId());
                        } else {
                            final String msg = response != null ? response.getMessage() : "删除记忆失败";
                            errorMessage.set(msg);
                            LOG.warn("AI记忆删除失败: {}", msg);
                        }
                    });
                })
                .exceptionally(ex -> {
                    LOG.error("删除AI记忆异常: memoryId={}", memoryId, ex);
                    Platform.runLater(() -> errorMessage.set("网络异常，删除记忆失败"));
                    return null;
                });
    }

    // ========== Property Getters ==========

    public ListProperty<AiProfile> aiListProperty() {
        return aiList;
    }

    public ObjectProperty<AiProfile> selectedAiProperty() {
        return selectedAi;
    }

    public ListProperty<AiMemory> memoriesProperty() {
        return memories;
    }

    public BooleanProperty loadingProperty() {
        return loading;
    }

    public StringProperty errorMessageProperty() {
        return errorMessage;
    }

    public StringProperty successMessageProperty() {
        return successMessage;
    }
}
