package com.voluntary.chat.server.controller;

import com.voluntary.chat.server.dto.response.FileUploadResponse;
import com.voluntary.chat.server.service.FileUploadService;
import com.voluntary.chat.server.service.ImageUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FileController 单元测试
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@DisplayName("FileController 测试")
class FileControllerTest {

    private MockMvc mockMvc;
    private ImageUploadService imageUploadService;
    private FileUploadService fileUploadService;

    @BeforeEach
    void setUp() {
        imageUploadService = mock(ImageUploadService.class);
        fileUploadService = mock(FileUploadService.class);
        FileController controller = new FileController(imageUploadService, fileUploadService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("上传头像 - 成功")
    void uploadAvatar_shouldReturnOk() throws Exception {
        when(imageUploadService.uploadAvatar(any())).thenReturn("/avatars/test-avatar.jpg");

        final MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.jpg", "image/jpeg", "fake-image-data".getBytes());

        mockMvc.perform(multipart("/api/file/upload/avatar")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("/avatars/test-avatar.jpg"));
    }

    @Test
    @DisplayName("上传群头像 - 成功")
    void uploadGroupAvatar_shouldReturnOk() throws Exception {
        when(imageUploadService.uploadAvatar(any())).thenReturn("/avatars/group-avatar.jpg");

        final MockMultipartFile file = new MockMultipartFile(
                "file", "group.png", "image/png", "fake-png-data".getBytes());

        mockMvc.perform(multipart("/api/file/upload/group-avatar")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("/avatars/group-avatar.jpg"));
    }

    @Test
    @DisplayName("上传 AI 头像 - 成功")
    void uploadAiAvatar_shouldReturnOk() throws Exception {
        when(imageUploadService.uploadAvatar(any())).thenReturn("/avatars/ai-avatar.jpg");

        final MockMultipartFile file = new MockMultipartFile(
                "file", "ai.gif", "image/gif", "fake-gif-data".getBytes());

        mockMvc.perform(multipart("/api/file/upload/ai-avatar")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("/avatars/ai-avatar.jpg"));
    }

    @Test
    @DisplayName("上传聊天文件 - 成功")
    void uploadFile_shouldReturnOk() throws Exception {
        final FileUploadResponse mockResp = FileUploadResponse.builder()
                .fileId("uuid-file-id")
                .url("/chat-files/2026/07/01/uuid-file-id.pdf")
                .fileName("report.pdf")
                .size(1024000L)
                .fileType("application/pdf")
                .build();

        when(fileUploadService.uploadFile(any())).thenReturn(mockResp);

        final MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", "fake-pdf-data".getBytes());

        mockMvc.perform(multipart("/api/file/upload/file")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.fileId").value("uuid-file-id"))
                .andExpect(jsonPath("$.data.url").value("/chat-files/2026/07/01/uuid-file-id.pdf"))
                .andExpect(jsonPath("$.data.fileName").value("report.pdf"))
                .andExpect(jsonPath("$.data.size").value(1024000))
                .andExpect(jsonPath("$.data.fileType").value("application/pdf"));
    }

    @Test
    @DisplayName("上传聊天文件 - 无文件时返回 400")
    void uploadFile_shouldReturn400_whenNoFile() throws Exception {
        mockMvc.perform(multipart("/api/file/upload/file"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("上传头像 - 无文件时返回 400")
    void uploadAvatar_shouldReturn400_whenNoFile() throws Exception {
        mockMvc.perform(multipart("/api/file/upload/avatar"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("上传聊天文件 - 大文件成功")
    void uploadFile_shouldSucceed_withLargeFile() throws Exception {
        final FileUploadResponse mockResp = FileUploadResponse.builder()
                .fileId("uuid-large")
                .url("/chat-files/2026/07/01/uuid-large.bin")
                .fileName("large.bin")
                .size(50L * 1024 * 1024)
                .fileType("application/octet-stream")
                .build();

        when(fileUploadService.uploadFile(any())).thenReturn(mockResp);

        final byte[] largeData = new byte[50 * 1024 * 1024];
        final MockMultipartFile file = new MockMultipartFile(
                "file", "large.bin", "application/octet-stream", largeData);

        mockMvc.perform(multipart("/api/file/upload/file")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.size").value(50L * 1024 * 1024));
    }
}