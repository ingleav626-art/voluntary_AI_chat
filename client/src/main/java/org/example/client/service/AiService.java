package org.example.client.service;

import java.net.http.HttpRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.example.client.model.AiGroupConfig;
import org.example.client.model.AiMemory;
import org.example.client.model.AiProfile;
import org.example.client.model.ApiResponse;
import org.example.client.model.PageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AI 角色管理服务
 *
 * <p>封装 AI 角色列表、创建、修改、删除、记忆查询等 REST 接口。</p>
 */
public final class AiService extends BaseHttpService {

    private static final Logger LOG = LoggerFactory.getLogger(AiService.class);

    private static final AiService INSTANCE = new AiService();

    /** AI 模块基础路径 */
    private static final String AI_PATH = "/ai";

    /** 默认每页数量 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    private AiService() {
        // 单例模式
    }

    public static AiService getInstance() {
        return INSTANCE;
    }

    /**
     * 获取 AI 角色列表
     *
     * @param page 页码
     * @param size 每页数量
     * @return 异步结果
     */
    public CompletableFuture<ApiResponse<PageResult<AiProfile>>> getAiList(final int page, final int size) {
        if (!checkLoginStatus()) {
            return CompletableFuture.completedFuture(createNotLoggedInResponse());
        }

        final String path = AI_PATH + "/list?page=" + page + "&size=" + size;
        final HttpRequest request = buildGetRequest(path).build();

        LOG.info("获取AI角色列表: page={}, size={}", page, size);

        return sendRequest(request, getTypeFactory().constructParametricType(
                ApiResponse.class,
                getTypeFactory().constructParametricType(PageResult.class, AiProfile.class)));
    }

    /**
     * 创建 AI 角色
     *
     * @param profile AI 角色信息
     * @return 异步结果，返回创建的 AI ID
     */
    public CompletableFuture<ApiResponse<Long>> createAiProfile(final AiProfile profile) {
        if (!checkLoginStatus()) {
            return CompletableFuture.completedFuture(createNotLoggedInResponse());
        }

        final Map<String, Object> body = new HashMap<>();
        if (profile.getName() != null) {
            body.put("name", profile.getName());
        }
        if (profile.getAvatar() != null) {
            body.put("avatar", profile.getAvatar());
        }
        if (profile.getPersona() != null) {
            body.put("persona", profile.getPersona());
        }
        if (profile.getSystemPrompt() != null) {
            body.put("systemPrompt", profile.getSystemPrompt());
        }
        if (profile.getModelProvider() != null) {
            body.put("modelProvider", profile.getModelProvider());
        }
        if (profile.getModel() != null) {
            body.put("model", profile.getModel());
        }
        if (profile.getApiKey() != null) {
            body.put("apiKey", profile.getApiKey());
        }
        if (profile.getIsGroup() != null) {
            body.put("isGroup", profile.getIsGroup());
        }
        if (profile.getTemperature() != null) {
            body.put("temperature", profile.getTemperature());
        }
        if (profile.getMaxTokens() != null) {
            body.put("maxTokens", profile.getMaxTokens());
        }

        final HttpRequest httpRequest = buildPostRequest(AI_PATH + "/create", body).build();

        LOG.info("创建AI角色: name={}, provider={}, model={}",
                profile.getName(), profile.getModelProvider(), profile.getModel());

        return sendRequest(httpRequest, getTypeFactory().constructParametricType(
                ApiResponse.class, Long.class));
    }

    /**
     * 修改 AI 角色
     *
     * @param aiId    AI 角色ID
     * @param profile 修改内容
     * @return 异步结果
     */
    public CompletableFuture<ApiResponse<Void>> updateAiProfile(final Long aiId, final AiProfile profile) {
        if (!checkLoginStatus()) {
            return CompletableFuture.completedFuture(createNotLoggedInResponse());
        }

        final Map<String, Object> body = new HashMap<>();
        if (profile.getName() != null) {
            body.put("name", profile.getName());
        }
        if (profile.getAvatar() != null) {
            body.put("avatar", profile.getAvatar());
        }
        if (profile.getPersona() != null) {
            body.put("persona", profile.getPersona());
        }
        if (profile.getSystemPrompt() != null) {
            body.put("systemPrompt", profile.getSystemPrompt());
        }
        if (profile.getModel() != null) {
            body.put("model", profile.getModel());
        }
        if (profile.getApiKey() != null) {
            body.put("apiKey", profile.getApiKey());
        }
        if (profile.getIsGroup() != null) {
            body.put("isGroup", profile.getIsGroup());
        }
        if (profile.getTemperature() != null) {
            body.put("temperature", profile.getTemperature());
        }
        if (profile.getMaxTokens() != null) {
            body.put("maxTokens", profile.getMaxTokens());
        }

        final HttpRequest httpRequest = buildPutRequest(AI_PATH + "/" + aiId, body).build();

        LOG.info("修改AI角色: aiId={}", aiId);

        return sendRequest(httpRequest, getTypeFactory().constructParametricType(
                ApiResponse.class, Void.class));
    }

    /**
     * 删除 AI 角色
     *
     * @param aiId AI 角色ID
     * @return 异步结果
     */
    public CompletableFuture<ApiResponse<Void>> deleteAiProfile(final Long aiId) {
        if (!checkLoginStatus()) {
            return CompletableFuture.completedFuture(createNotLoggedInResponse());
        }

        final HttpRequest httpRequest = buildDeleteRequest(AI_PATH + "/" + aiId).build();

        LOG.info("删除AI角色: aiId={}", aiId);

        return sendRequest(httpRequest, getTypeFactory().constructParametricType(
                ApiResponse.class, Void.class));
    }

    /**
     * 获取 AI 记忆列表
     *
     * @param aiId AI 角色ID
     * @param page 页码
     * @param size 每页数量
     * @return 异步结果
     */
    public CompletableFuture<ApiResponse<PageResult<AiMemory>>> getAiMemories(
            final Long aiId, final int page, final int size) {
        if (!checkLoginStatus()) {
            return CompletableFuture.completedFuture(createNotLoggedInResponse());
        }

        final String path = AI_PATH + "/" + aiId + "/memories?page=" + page + "&size=" + size;
        final HttpRequest request = buildGetRequest(path).build();

        LOG.info("获取AI记忆: aiId={}, page={}, size={}", aiId, page, size);

        return sendRequest(request, getTypeFactory().constructParametricType(
                ApiResponse.class,
                getTypeFactory().constructParametricType(PageResult.class, AiMemory.class)));
    }

    /**
     * 创建群 AI 配置
     *
     * @param groupId 群组ID
     * @param config  配置信息
     * @return 异步结果，返回配置ID
     */
    public CompletableFuture<ApiResponse<Long>> createGroupConfig(
            final Long groupId, final AiGroupConfig config) {
        if (!checkLoginStatus()) {
            return CompletableFuture.completedFuture(createNotLoggedInResponse());
        }

        final Map<String, Object> body = new HashMap<>();
        body.put("aiId", config.getAiId());
        if (config.getTriggerKeywords() != null) {
            body.put("triggerKeywords", config.getTriggerKeywords());
        }
        if (config.getTriggerProbability() != null) {
            body.put("triggerProbability", config.getTriggerProbability());
        }
        if (config.getIsEnabled() != null) {
            body.put("isEnabled", config.getIsEnabled());
        }

        final HttpRequest httpRequest = buildPostRequest(
                AI_PATH + "/group/" + groupId + "/config", body).build();

        LOG.info("创建群AI配置: groupId={}, aiId={}", groupId, config.getAiId());

        return sendRequest(httpRequest, getTypeFactory().constructParametricType(
                ApiResponse.class, Long.class));
    }

    /**
     * 获取群 AI 配置列表
     *
     * @param groupId 群组ID
     * @return 异步结果
     */
    public CompletableFuture<ApiResponse<java.util.List<AiGroupConfig>>> getGroupConfigs(final Long groupId) {
        if (!checkLoginStatus()) {
            return CompletableFuture.completedFuture(createNotLoggedInResponse());
        }

        final String path = AI_PATH + "/group/" + groupId + "/configs";
        final HttpRequest request = buildGetRequest(path).build();

        LOG.info("获取群AI配置: groupId={}", groupId);

        return sendRequest(request, getTypeFactory().constructParametricType(
                ApiResponse.class,
                getTypeFactory().constructCollectionType(java.util.ArrayList.class, AiGroupConfig.class)));
    }
}
