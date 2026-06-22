package com.voluntary.chat.server.controller;

import com.voluntary.chat.common.dto.PageResult;
import com.voluntary.chat.common.constant.MessageTypes;
import com.voluntary.chat.common.model.WebSocketMessage;
import com.voluntary.chat.server.common.ApiResult;
import com.voluntary.chat.server.dto.request.MarkReadRequest;
import com.voluntary.chat.server.dto.request.RecallMessageRequest;
import com.voluntary.chat.server.dto.request.SendMessageRequest;
import com.voluntary.chat.server.dto.response.ImageUploadResponse;
import com.voluntary.chat.server.dto.response.MessageResponse;
import com.voluntary.chat.server.dto.response.RecallMessageResponse;
import com.voluntary.chat.server.dto.response.SendMessageResponse;
import com.voluntary.chat.server.security.SecurityUtils;
import com.voluntary.chat.server.service.ImageUploadService;
import com.voluntary.chat.server.service.MessageService;
import com.voluntary.chat.server.websocket.ChatWebSocketHandler;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/message")
@RequiredArgsConstructor
public class MessageController {

  /** 私聊sessionId分割后的部分数量（格式：p_{min}_{max}） */
  private static final int PRIVATE_SESSION_PARTS_COUNT = 3;
  /** 私聊sessionId中第一个用户ID的索引 */
  private static final int PRIVATE_SESSION_ID1_INDEX = 1;
  /** 私聊sessionId中第二个用户ID的索引 */
  private static final int PRIVATE_SESSION_ID2_INDEX = 2;

  private final MessageService messageService;
  private final ImageUploadService imageUploadService;
  private final ChatWebSocketHandler chatWebSocketHandler;

  @PostMapping("/send")
  public ApiResult<SendMessageResponse> sendMessage(@Valid @RequestBody SendMessageRequest request) {
    Long userId = SecurityUtils.getCurrentUserId();
    SendMessageResponse response = messageService.sendMessage(userId, request);
    return ApiResult.ok("发送成功", response);
  }

  /**
   * 上传图片
   *
   * <p>
   * 支持 JPEG/PNG/GIF/WebP 格式，最大 10MB。
   * 上传后自动压缩（最大宽度 1080px）并生成缩略图。
   * </p>
   */
  @PostMapping(value = "/upload/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResult<ImageUploadResponse> uploadImage(@RequestParam("file") MultipartFile file) {
    ImageUploadResponse response = imageUploadService.uploadImage(file);
    return ApiResult.ok("上传成功", response);
  }

  @GetMapping("/history")
  public ApiResult<PageResult<MessageResponse>> getHistory(
      @RequestParam String sessionId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    Long userId = SecurityUtils.getCurrentUserId();
    PageResult<MessageResponse> result = messageService.getHistory(sessionId, userId, page, size);
    return ApiResult.ok(result);
  }

  @PostMapping("/recall")
  public ApiResult<RecallMessageResponse> recallMessage(@Valid @RequestBody RecallMessageRequest request) {
    Long userId = SecurityUtils.getCurrentUserId();
    RecallMessageResponse response = messageService.recallMessage(userId, Long.parseLong(request.getMessageId()));

    // 通过 WebSocket 推送撤回通知给对方（私聊）或群成员（群聊）
    WebSocketMessage recallMsg = WebSocketMessage.builder()
        .id(String.valueOf(System.currentTimeMillis()))
        .type(MessageTypes.MESSAGE_RECALL)
        .data(Map.of(
            "messageId", response.getMessageId(),
            "sessionId", response.getSessionId(),
            "senderId", response.getSenderId(),
            "recallTime", LocalDateTime.now().toString()))
        .build();

    String sessionId = response.getSessionId();
    if (sessionId.startsWith("g_")) {
      // 群聊：推送给群内所有成员（除了撤回操作者自己）
      chatWebSocketHandler.broadcastToGroupExcept(sessionId, userId, recallMsg);
    } else {
      // 私聊：推送给对方
      Long targetUserId = resolveTargetUserId(userId, sessionId);
      if (targetUserId != null) {
        chatWebSocketHandler.sendToUser(targetUserId, recallMsg);
      }
      // 同时推送给撤回者自己，以便多端同步
      chatWebSocketHandler.sendToUser(userId, recallMsg);
    }

    return ApiResult.ok("已撤回", response);
  }

  /**
   * 从私聊 sessionId 中解析对方的 userId
   * 格式：p_{min}_{max}
   */
  private Long resolveTargetUserId(Long currentUserId, String sessionId) {
    String[] parts = sessionId.split("_");
    if (parts.length != PRIVATE_SESSION_PARTS_COUNT) {
      return null;
    }
    Long id1 = Long.parseLong(parts[PRIVATE_SESSION_ID1_INDEX]);
    Long id2 = Long.parseLong(parts[PRIVATE_SESSION_ID2_INDEX]);
    return id1.equals(currentUserId) ? id2 : id1;
  }

  @PostMapping("/read")
  public ApiResult<Void> markRead(@Valid @RequestBody MarkReadRequest request) {
    Long userId = SecurityUtils.getCurrentUserId();
    messageService.markRead(userId, request);

    // 通过 WebSocket 推送已读通知给消息发送者
    List<Long> messageIds = request.getMessageIds();
    if (messageIds != null && !messageIds.isEmpty()) {
      Long lastReadMessageId = messageIds.get(messageIds.size() - 1);
      WebSocketMessage readReceipt = WebSocketMessage.builder()
          .id(String.valueOf(System.currentTimeMillis()))
          .type(MessageTypes.READ_RECEIPT)
          .data(Map.of(
              "sessionId", request.getSessionId(),
              "userId", userId,
              "lastReadMessageId", lastReadMessageId,
              "readTime", LocalDateTime.now().toString()))
          .build();
      chatWebSocketHandler.sendToUser(userId, readReceipt);
    }

    return ApiResult.ok(null);
  }
}
