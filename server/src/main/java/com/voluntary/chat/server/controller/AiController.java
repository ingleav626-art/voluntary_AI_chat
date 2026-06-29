package com.voluntary.chat.server.controller;

import com.voluntary.chat.common.dto.PageResult;
import com.voluntary.chat.server.common.ApiResult;
import com.voluntary.chat.server.dto.request.AiGroupConfigRequest;
import com.voluntary.chat.server.dto.request.CreateAiProfileRequest;
import com.voluntary.chat.server.dto.request.UpdateAiProfileRequest;
import com.voluntary.chat.server.dto.response.AiGroupConfigResponse;
import com.voluntary.chat.server.dto.response.AiMemoryResponse;
import com.voluntary.chat.server.dto.response.AiProfileResponse;
import com.voluntary.chat.server.entity.AiMemory;
import com.voluntary.chat.server.security.SecurityUtils;
import com.voluntary.chat.server.service.AiGroupConfigService;
import com.voluntary.chat.server.service.AiMemoryService;
import com.voluntary.chat.server.service.AiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * AI 角色管理接口
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;
    private final AiGroupConfigService aiGroupConfigService;
    private final AiMemoryService aiMemoryService;

    /**
     * 获取 AI 列表
     */
    @GetMapping("/list")
    public ApiResult<PageResult<AiProfileResponse>> listAiProfiles(
            @RequestParam(defaultValue = "1") final Integer page,
            @RequestParam(defaultValue = "20") final Integer size) {
        final Long userId = SecurityUtils.getCurrentUserId();
        final PageResult<AiProfileResponse> result = aiService.listAiProfiles(userId, page, size);
        return ApiResult.ok(result);
    }

    /**
     * 创建 AI 角色
     */
    @PostMapping("/create")
    public ApiResult<Long> createAiProfile(@Valid @RequestBody final CreateAiProfileRequest request) {
        final Long userId = SecurityUtils.getCurrentUserId();
        final Long aiId = aiService.createAiProfile(userId, request);
        return ApiResult.ok("创建成功", aiId);
    }

    /**
     * 修改 AI 角色
     */
    @PutMapping("/{aiId}")
    public ApiResult<Void> updateAiProfile(
            @PathVariable final Long aiId,
            @Valid @RequestBody final UpdateAiProfileRequest request) {
        final Long userId = SecurityUtils.getCurrentUserId();
        aiService.updateAiProfile(userId, aiId, request);
        return ApiResult.ok("修改成功", null);
    }

    /**
     * 删除 AI 角色
     */
    @DeleteMapping("/{aiId}")
    public ApiResult<Void> deleteAiProfile(@PathVariable final Long aiId) {
        final Long userId = SecurityUtils.getCurrentUserId();
        aiService.deleteAiProfile(userId, aiId);
        return ApiResult.ok("已删除", null);
    }

    /**
     * 创建群 AI 配置
     */
    @PostMapping("/group/{groupId}/config")
    public ApiResult<Long> createGroupConfig(
            @PathVariable final Long groupId,
            @Valid @RequestBody final AiGroupConfigRequest request) {
        final Long userId = SecurityUtils.getCurrentUserId();
        final Long configId = aiGroupConfigService.createGroupConfig(groupId, userId, request);
        return ApiResult.ok("配置成功", configId);
    }

    /**
     * 获取群 AI 配置列表
     */
    @GetMapping("/group/{groupId}/configs")
    public ApiResult<List<AiGroupConfigResponse>> getGroupConfigs(@PathVariable final Long groupId) {
        final List<AiGroupConfigResponse> configs = aiGroupConfigService.getGroupConfigs(groupId);
        return ApiResult.ok(configs);
    }

    /**
     * 删除 AI 记忆
     */
    @DeleteMapping("/{aiId}/memories/{memoryId}")
    public ApiResult<Void> deleteMemory(
            @PathVariable final Long aiId,
            @PathVariable final Long memoryId) {
        final Long userId = SecurityUtils.getCurrentUserId();
        aiMemoryService.deleteMemory(memoryId, userId);
        return ApiResult.ok("已删除", null);
    }

    /**
     * 修改群 AI 配置
     */
    @PutMapping("/group/{groupId}/config/{configId}")
    public ApiResult<Void> updateGroupConfig(
            @PathVariable final Long groupId,
            @PathVariable final Long configId,
            @Valid @RequestBody final AiGroupConfigRequest request) {
        final Long userId = SecurityUtils.getCurrentUserId();
        aiGroupConfigService.updateGroupConfig(groupId, configId, userId, request);
        return ApiResult.ok("修改成功", null);
    }

    /**
     * 删除群 AI 配置
     */
    @DeleteMapping("/group/{groupId}/config/{configId}")
    public ApiResult<Void> deleteGroupConfig(
            @PathVariable final Long groupId,
            @PathVariable final Long configId) {
        final Long userId = SecurityUtils.getCurrentUserId();
        aiGroupConfigService.deleteGroupConfig(configId, userId);
        return ApiResult.ok("已删除", null);
    }

    /**
     * 获取 AI 记忆列表
     */
    @GetMapping("/{aiId}/memories")
    public ApiResult<PageResult<AiMemoryResponse>> getMemories(
            @PathVariable final Long aiId,
            @RequestParam(defaultValue = "1") final Integer page,
            @RequestParam(defaultValue = "10") final Integer size) {
        final Long userId = SecurityUtils.getCurrentUserId();
        final PageResult<AiMemory> result = aiMemoryService.getMemories(aiId, userId, page, size);

        final List<AiMemoryResponse> memories = result.getList().stream()
                .map(memory -> AiMemoryResponse.builder()
                        .memoryId(memory.getId())
                        .summary(memory.getSummary())
                        .keywords(memory.getKeywords())
                        .importance(memory.getImportance())
                        .createTime(memory.getCreateTime() != null ? memory.getCreateTime().toString() : null)
                        .build())
                .collect(Collectors.toList());

        return ApiResult.ok(PageResult.<AiMemoryResponse>builder()
                .list(memories)
                .total(result.getTotal())
                .page(result.getPage())
                .size(result.getSize())
                .build());
    }
}