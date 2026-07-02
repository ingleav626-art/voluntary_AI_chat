package org.example.client.util;

import org.example.client.config.ClientConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ImageUtils 工具类单元测试
 *
 * <p>
 * 测试 resolveImageUrl 对各类 URL 的解析行为，
 * 覆盖本地模式、热点/远程模式下的路径拼接。
 * </p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@DisplayName("ImageUtils 工具类测试")
class ImageUtilsTest {

    @Test
    @DisplayName("null 输入返回 null")
    void resolveImageUrl_nullInput() {
        assertNull(ImageUtils.resolveImageUrl(null));
    }

    @Test
    @DisplayName("空字符串输入返回空字符串")
    void resolveImageUrl_emptyInput() {
        assertEquals("", ImageUtils.resolveImageUrl(""));
    }

    @Test
    @DisplayName("完整 HTTP URL 原样返回")
    void resolveImageUrl_fullHttpUrl() {
        String result = ImageUtils.resolveImageUrl("http://example.com/image.jpg");
        assertEquals("http://example.com/image.jpg", result);
    }

    @Test
    @DisplayName("完整 HTTPS URL 原样返回")
    void resolveImageUrl_fullHttpsUrl() {
        String result = ImageUtils.resolveImageUrl("https://example.com/image.jpg");
        assertEquals("https://example.com/image.jpg", result);
    }

    @Test
    @DisplayName("file: 协议 URL 原样返回")
    void resolveImageUrl_fileProtocol() {
        String result = ImageUtils.resolveImageUrl("file:///C:/images/avatar.jpg");
        assertEquals("file:///C:/images/avatar.jpg", result);
    }

    @Test
    @DisplayName("头像相对路径拼接正确")
    void resolveImageUrl_avatarPath() {
        String result = ImageUtils.resolveImageUrl("/avatars/abc123.jpg");
        String baseUrl = ClientConfig.getInstance().getBaseUrl();
        String serverRoot = baseUrl.endsWith("/api")
                ? baseUrl.substring(0, baseUrl.length() - "/api".length())
                : baseUrl;
        assertEquals(serverRoot + "/avatars/abc123.jpg", result);
    }

    @Test
    @DisplayName("聊天图片相对路径拼接正确")
    void resolveImageUrl_chatImagePath() {
        String result = ImageUtils.resolveImageUrl("/files/2026/07/01/test.jpg");
        String baseUrl = ClientConfig.getInstance().getBaseUrl();
        String serverRoot = baseUrl.endsWith("/api")
                ? baseUrl.substring(0, baseUrl.length() - "/api".length())
                : baseUrl;
        assertEquals(serverRoot + "/files/2026/07/01/test.jpg", result);
    }

    @Test
    @DisplayName("聊天文件相对路径拼接正确")
    void resolveImageUrl_chatFilePath() {
        String result = ImageUtils.resolveImageUrl("/chat-files/2026/07/01/report.pdf");
        String baseUrl = ClientConfig.getInstance().getBaseUrl();
        String serverRoot = baseUrl.endsWith("/api")
                ? baseUrl.substring(0, baseUrl.length() - "/api".length())
                : baseUrl;
        assertEquals(serverRoot + "/chat-files/2026/07/01/report.pdf", result);
    }

    @Test
    @DisplayName("缩略图相对路径拼接正确")
    void resolveImageUrl_thumbnailPath() {
        String result = ImageUtils.resolveImageUrl("/files/2026/07/01/test_thumb.jpg");
        assertNotNull(result);
        assertTrue(result.contains("/files/2026/07/01/test_thumb.jpg"));
    }

    @Test
    @DisplayName("路径包含中文编码时不破坏URL")
    void resolveImageUrl_chineseFileName() {
        String result = ImageUtils.resolveImageUrl("/files/2026/07/01/%E5%9B%BE%E7%89%87.jpg");
        assertNotNull(result);
        assertTrue(result.contains("%E5%9B%BE%E7%89%87.jpg"));
    }

    @Test
    @DisplayName("热点模式下远程路径拼接正确")
    void resolveImageUrl_shouldResolveRemoteUrl() {
        // 无论 baseUrl 是什么（localhost 或远程地址），
        // resolveImageUrl 都应正确拼接
        String result = ImageUtils.resolveImageUrl("/files/2026/07/01/photo.jpg");
        assertNotNull(result);
        // 结果应包含原始路径
        assertTrue(result.contains("/files/2026/07/01/photo.jpg"));
        // 结果应以 baseUrl 的根路径开头
        String baseUrl = ClientConfig.getInstance().getBaseUrl();
        String serverRoot = baseUrl.endsWith("/api")
                ? baseUrl.substring(0, baseUrl.length() - "/api".length())
                : baseUrl;
        assertTrue(result.startsWith(serverRoot), "应拼接服务器根路径: " + result);
    }
}