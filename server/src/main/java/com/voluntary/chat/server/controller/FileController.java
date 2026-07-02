package com.voluntary.chat.server.controller;

import com.voluntary.chat.server.common.ApiResult;
import com.voluntary.chat.server.dto.response.FileUploadResponse;
import com.voluntary.chat.server.service.FileUploadService;
import com.voluntary.chat.server.service.ImageUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传控制器
 *
 * <p>
 * 提供头像（用户/群组）上传专用端点，与聊天图片上传分离。
 * 上传后返回可直接保存到对应实体的 avatar 字段的 URL。
 * 同时提供通用文件上传端点，用于聊天文件消息。
 * </p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileController {

  private final ImageUploadService imageUploadService;
  private final FileUploadService fileUploadService;

  /**
   * 上传用户头像
   *
   * <p>
   * 保存到 {@code uploads/avatars/} 目录，返回可直接存入 {@code user.avatar} 的 URL。
   * 前端上传后调用 {@code PUT /api/user/profile} 更新头像字段。
   * </p>
   *
   * @param file 头像图片（JPEG/PNG/GIF/WebP，最大 10MB）
   * @return 图片 URL
   */
  @PostMapping(value = "/upload/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResult<String> uploadAvatar(@RequestParam("file") final MultipartFile file) {
    final String url = imageUploadService.uploadAvatar(file);
    log.info("用户头像上传成功: url={}", url);
    return ApiResult.ok("上传成功", url);
  }

  /**
   * 上传群组头像
   *
   * <p>
   * 保存到 {@code uploads/avatars/} 目录，返回可直接存入 {@code chat_group.avatar} 的 URL。
   * 前端上传后调用 {@code PUT /api/group/{groupId}} 更新头像字段。
   * </p>
   *
   * @param file 群头像图片（JPEG/PNG/GIF/WebP，最大 10MB）
   * @return 图片 URL
   */
  @PostMapping(value = "/upload/group-avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResult<String> uploadGroupAvatar(@RequestParam("file") final MultipartFile file) {
    final String url = imageUploadService.uploadAvatar(file);
    log.info("群头像上传成功: url={}", url);
    return ApiResult.ok("上传成功", url);
  }

  /**
   * 上传 AI 角色头像
   *
   * <p>
   * 保存到 {@code uploads/avatars/} 目录，返回可直接存入 {@code ai_profile.avatar} 的 URL。
   * 前端上传后调用 {@code PUT /api/ai/profile/{aiId}} 更新头像字段。
   * </p>
   *
   * @param file AI 头像图片（JPEG/PNG/GIF/WebP，最大 10MB）
   * @return 图片 URL
   */
  @PostMapping(value = "/upload/ai-avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResult<String> uploadAiAvatar(@RequestParam("file") final MultipartFile file) {
    final String url = imageUploadService.uploadAvatar(file);
    log.info("AI头像上传成功: url={}", url);
    return ApiResult.ok("上传成功", url);
  }

  /**
   * 上传聊天文件
   *
   * <p>
   * 通用文件上传，支持最大 100MB。
   * 上传后返回文件 URL、文件名、大小等信息。
   * 前端先上传文件获取 URL，再通过 WebSocket 发送 FILE 类型消息。
   * </p>
   *
   * @param file 上传的文件（最大 100MB）
   * @return 文件上传结果
   */
  @PostMapping(value = "/upload/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResult<FileUploadResponse> uploadFile(@RequestParam("file") final MultipartFile file) {
    final FileUploadResponse response = fileUploadService.uploadFile(file);
    log.info("聊天文件上传成功: fileId={}, fileName={}", response.getFileId(), response.getFileName());
    return ApiResult.ok("上传成功", response);
  }
}
