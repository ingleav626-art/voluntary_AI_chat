package com.voluntary.chat.server.controller;

import com.voluntary.chat.common.dto.PageResult;
import com.voluntary.chat.server.common.ApiResult;
import com.voluntary.chat.server.dto.request.MarkReadRequest;
import com.voluntary.chat.server.dto.request.RecallMessageRequest;
import com.voluntary.chat.server.dto.request.SendMessageRequest;
import com.voluntary.chat.server.dto.response.ImageUploadResponse;
import com.voluntary.chat.server.dto.response.MessageResponse;
import com.voluntary.chat.server.dto.response.SendMessageResponse;
import com.voluntary.chat.server.security.SecurityUtils;
import com.voluntary.chat.server.service.ImageUploadService;
import com.voluntary.chat.server.service.MessageService;
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

@RestController
@RequestMapping("/api/message")
@RequiredArgsConstructor
public class MessageController {

  private final MessageService messageService;
  private final ImageUploadService imageUploadService;

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
  public ApiResult<Void> recallMessage(@Valid @RequestBody RecallMessageRequest request) {
    Long userId = SecurityUtils.getCurrentUserId();
    messageService.recallMessage(userId, Long.parseLong(request.getMessageId()));
    return ApiResult.ok("已撤回", null);
  }

  @PostMapping("/read")
  public ApiResult<Void> markRead(@Valid @RequestBody MarkReadRequest request) {
    Long userId = SecurityUtils.getCurrentUserId();
    messageService.markRead(userId, request);
    return ApiResult.ok(null);
  }
}
