package com.voluntary.chat.server.controller;

import com.voluntary.chat.common.dto.PageResult;
import com.voluntary.chat.server.common.ApiResult;
import com.voluntary.chat.server.dto.response.ConversationResponse;
import com.voluntary.chat.server.security.SecurityUtils;
import com.voluntary.chat.server.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversation")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @GetMapping("/list")
    public ApiResult<PageResult<ConversationResponse>> getConversations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        Long userId = SecurityUtils.getCurrentUserId();
        PageResult<ConversationResponse> result = conversationService.getConversations(userId, page, size, keyword);
        return ApiResult.ok(result);
    }
}
