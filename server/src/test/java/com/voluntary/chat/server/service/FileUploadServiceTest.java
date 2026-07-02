package com.voluntary.chat.server.service;

import com.voluntary.chat.common.exception.BusinessException;
import com.voluntary.chat.common.exception.ErrorCode;
import com.voluntary.chat.server.dto.response.FileUploadResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * FileUploadService 单元测试
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@DisplayName("FileUploadService 单元测试")
class FileUploadServiceTest {

    private FileUploadService fileUploadService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        fileUploadService = new FileUploadService(tempDir.toString());
    }

    @Test
    @DisplayName("上传文本文件成功")
    void uploadFile_shouldSucceed_withTextFile() throws IOException {
        final byte[] content = "Hello, World!".getBytes();
        MultipartFile file = createMockFile("test.txt", "text/plain", content);

        FileUploadResponse response = fileUploadService.uploadFile(file);

        assertEquals("test.txt", response.getFileName());
        assertEquals(content.length, response.getSize());
        assertEquals("text/plain", response.getFileType());
        assertNotNull(response.getFileId());
        assertNotNull(response.getUrl());
        // 验证返回相对路径
        assertTrue(response.getUrl().startsWith("/chat-files/"));
        assertTrue(response.getUrl().endsWith(".txt"));
    }

    @Test
    @DisplayName("上传文件带无扩展文件名成功")
    void uploadFile_shouldSucceed_withNoExtension() throws IOException {
        final byte[] content = "no extension file".getBytes();
        MultipartFile file = createMockFile("README", "text/plain", content);

        FileUploadResponse response = fileUploadService.uploadFile(file);

        assertEquals("README", response.getFileName());
        assertNotNull(response.getUrl());
        // 无扩展名时 URL 不应包含 .
        assertFalse(response.getUrl().endsWith("."));
    }

    @Test
    @DisplayName("上传空文件名使用默认名")
    void uploadFile_shouldUseDefaultName_whenFileNameEmpty() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("");
        when(file.getSize()).thenReturn(100L);
        when(file.getContentType()).thenReturn("application/octet-stream");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream("test".getBytes()));

        FileUploadResponse response = fileUploadService.uploadFile(file);

        assertEquals("unknown", response.getFileName());
    }

    @Test
    @DisplayName("上传文件 content type 为空时探测 MIME")
    void uploadFile_shouldProbeContentType_whenContentTypeNull() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("data.bin");
        when(file.getSize()).thenReturn(100L);
        when(file.getContentType()).thenReturn(null);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream("binary-data".getBytes()));

        FileUploadResponse response = fileUploadService.uploadFile(file);

        assertNotNull(response.getFileType());
        // 文件名来自 getOriginalFilename()
        assertEquals("data.bin", response.getFileName());
    }

    @Test
    @DisplayName("上传文件超过 100MB 抛出异常")
    void uploadFile_shouldThrow_whenFileTooLarge() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(101L * 1024 * 1024);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileUploadService.uploadFile(file));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    @DisplayName("上传空文件成功")
    void uploadFile_shouldSucceed_withEmptyFile() throws IOException {
        MultipartFile file = createMockFile("empty.txt", "text/plain", new byte[0]);

        FileUploadResponse response = fileUploadService.uploadFile(file);

        assertEquals(0, response.getSize());
        assertNotNull(response.getUrl());
    }

    @Test
    @DisplayName("上传 ZIP 文件成功")
    void uploadFile_shouldSucceed_withZipFile() throws IOException {
        final byte[] content = { 0x50, 0x4B, 0x03, 0x04, 0x00 }; // ZIP 文件头
        MultipartFile file = createMockFile("archive.zip", "application/zip", content);

        FileUploadResponse response = fileUploadService.uploadFile(file);

        assertEquals("archive.zip", response.getFileName());
        assertTrue(response.getUrl().endsWith(".zip"));
    }

    @Test
    @DisplayName("上传 PDF 文件成功")
    void uploadFile_shouldSucceed_withPdfFile() throws IOException {
        final byte[] content = { 0x25, 0x50, 0x44, 0x46 }; // PDF 文件头
        MultipartFile file = createMockFile("doc.pdf", "application/pdf", content);

        FileUploadResponse response = fileUploadService.uploadFile(file);

        assertEquals("doc.pdf", response.getFileName());
        assertTrue(response.getUrl().endsWith(".pdf"));
    }

    @Test
    @DisplayName("上传文件名包含中文成功")
    void uploadFile_shouldSucceed_withChineseFileName() throws IOException {
        final byte[] content = "中文文件内容".getBytes();
        MultipartFile file = createMockFile("报告.txt", "text/plain", content);

        FileUploadResponse response = fileUploadService.uploadFile(file);

        assertEquals("报告.txt", response.getFileName());
        assertNotNull(response.getUrl());
    }

    @Test
    @DisplayName("上传文件名包含特殊字符成功")
    void uploadFile_shouldSucceed_withSpecialCharsFileName() throws IOException {
        final byte[] content = "special chars".getBytes();
        MultipartFile file = createMockFile("test (1).v2.txt", "text/plain", content);

        FileUploadResponse response = fileUploadService.uploadFile(file);

        assertEquals("test (1).v2.txt", response.getFileName());
        assertTrue(response.getUrl().endsWith(".txt"));
    }

    @Test
    @DisplayName("IO 异常时抛出内部错误")
    void uploadFile_shouldThrow_whenIoError() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("test.txt");
        when(file.getSize()).thenReturn(100L);
        when(file.getContentType()).thenReturn("text/plain");
        // transferTo 是 void 方法，使用 doThrow 模拟写入失败
        doThrow(new IOException("磁盘写入失败")).when(file).transferTo(any(java.io.File.class));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileUploadService.uploadFile(file));
        assertEquals(ErrorCode.INTERNAL_ERROR, ex.getErrorCode());
    }

    /**
     * 创建指定内容的模拟文件 MultipartFile
     */
    private MultipartFile createMockFile(String fileName, String contentType, byte[] content)
            throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn(fileName);
        when(file.getSize()).thenReturn((long) content.length);
        when(file.getContentType()).thenReturn(contentType);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(content));
        return file;
    }
}