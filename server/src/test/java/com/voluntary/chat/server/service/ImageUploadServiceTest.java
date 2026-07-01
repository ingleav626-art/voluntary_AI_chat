package com.voluntary.chat.server.service;

import com.voluntary.chat.common.exception.BusinessException;
import com.voluntary.chat.common.exception.ErrorCode;
import com.voluntary.chat.server.dto.response.ImageUploadResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ImageUploadService 单元测试
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@DisplayName("ImageUploadService 单元测试")
class ImageUploadServiceTest {

    private ImageUploadService imageUploadService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        imageUploadService = new ImageUploadService(
                tempDir.toString(),
                tempDir.toString() + "/avatars",
                "http://localhost:8080/files",
                "http://localhost:8080/avatars");
    }

    @Test
    @DisplayName("上传 JPEG 图片成功")
    void uploadImage_shouldSucceed_withJpeg() throws IOException {
        MultipartFile file = createMockImageFile("image/jpeg", ".jpg", 800, 600);

        ImageUploadResponse response = imageUploadService.uploadImage(file);

        assertEquals("image/jpeg", response.getFileType());
        assertEquals(800, response.getWidth());
        assertEquals(600, response.getHeight());
        assertTrue(response.getSize() > 0);
        assertNotNull(response.getFileId());
        assertNotNull(response.getUrl());
        assertNotNull(response.getThumbnailUrl());
        assertTrue(response.getUrl().endsWith(".jpg"));
        assertTrue(response.getThumbnailUrl().endsWith(".jpg"));
    }

    @Test
    @DisplayName("上传 PNG 图片成功")
    void uploadImage_shouldSucceed_withPng() throws IOException {
        MultipartFile file = createMockImageFile("image/png", ".png", 400, 300);

        ImageUploadResponse response = imageUploadService.uploadImage(file);

        assertEquals("image/png", response.getFileType());
        assertEquals(400, response.getWidth());
        assertEquals(300, response.getHeight());
        assertTrue(response.getUrl().endsWith(".png"));
        assertTrue(response.getThumbnailUrl().endsWith(".png"));
    }

    @Test
    @DisplayName("上传 GIF 图片成功")
    void uploadImage_shouldSucceed_withGif() throws IOException {
        MultipartFile file = createMockImageFile("image/gif", ".gif", 200, 200);

        ImageUploadResponse response = imageUploadService.uploadImage(file);

        assertEquals("image/gif", response.getFileType());
        assertEquals(200, response.getWidth());
        assertEquals(200, response.getHeight());
        assertTrue(response.getUrl().endsWith(".gif"));
        assertTrue(response.getThumbnailUrl().endsWith(".gif"));
    }

    @Test
    @DisplayName("上传小尺寸图片不压缩")
    void uploadImage_shouldNotCompress_whenSmallImage() throws IOException {
        MultipartFile file = createMockImageFile("image/jpeg", ".jpg", 100, 100);

        ImageUploadResponse response = imageUploadService.uploadImage(file);

        assertEquals(100, response.getWidth());
        assertEquals(100, response.getHeight());
        assertNotNull(response.getUrl());
    }

    @Test
    @DisplayName("上传宽图自动压缩到 1080px")
    void uploadImage_shouldCompress_whenWidthExceedsMax() throws IOException {
        MultipartFile file = createMockImageFile("image/jpeg", ".jpg", 1920, 1080);

        ImageUploadResponse response = imageUploadService.uploadImage(file);

        // 原始尺寸应记录，但文件应已被压缩
        assertEquals(1920, response.getWidth());
        assertEquals(1080, response.getHeight());
        // 验证文件已保存
        assertNotNull(response.getUrl());
    }

    @Test
    @DisplayName("不支持的图片格式抛出异常")
    void uploadImage_shouldThrow_whenFormatNotSupported() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getContentType()).thenReturn("image/bmp");
        when(file.getSize()).thenReturn(1000L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> imageUploadService.uploadImage(file));
        assertEquals(ErrorCode.IMAGE_FORMAT_NOT_SUPPORTED, ex.getErrorCode());
    }

    @Test
    @DisplayName("空 content type 抛出异常")
    void uploadImage_shouldThrow_whenContentTypeNull() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getContentType()).thenReturn(null);
        when(file.getSize()).thenReturn(1000L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> imageUploadService.uploadImage(file));
        assertEquals(ErrorCode.IMAGE_FORMAT_NOT_SUPPORTED, ex.getErrorCode());
    }

    @Test
    @DisplayName("文件超过 10MB 抛出异常")
    void uploadImage_shouldThrow_whenFileTooLarge() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getSize()).thenReturn(11 * 1024 * 1024L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> imageUploadService.uploadImage(file));
        assertEquals(ErrorCode.IMAGE_SIZE_EXCEEDED, ex.getErrorCode());
    }

    @Test
    @DisplayName("无法解析的图片抛出异常")
    void uploadImage_shouldThrow_whenImageNotReadable() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getSize()).thenReturn(1000L);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream("not-an-image".getBytes()));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> imageUploadService.uploadImage(file));
        assertEquals(ErrorCode.IMAGE_FORMAT_NOT_SUPPORTED, ex.getErrorCode());
    }

    /**
     * 创建指定格式和尺寸的模拟图片 MultipartFile
     */
    private MultipartFile createMockImageFile(String contentType, String extension,
            int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String formatName = contentType.substring(contentType.lastIndexOf("/") + 1);
        ImageIO.write(image, formatName, baos);
        byte[] imageBytes = baos.toByteArray();

        MultipartFile file = mock(MultipartFile.class);
        when(file.getContentType()).thenReturn(contentType);
        when(file.getSize()).thenReturn((long) imageBytes.length);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(imageBytes));
        when(file.getOriginalFilename()).thenReturn("test" + extension);
        return file;
    }
}
