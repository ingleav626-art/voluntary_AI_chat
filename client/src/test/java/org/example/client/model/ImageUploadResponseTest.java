package org.example.client.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ImageUploadResponse 单元测试
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@DisplayName("ImageUploadResponse 单元测试")
class ImageUploadResponseTest {

    @Test
    @DisplayName("构造函数和 getter/setter 测试")
    void testGetterSetter() {
        final ImageUploadResponse response = new ImageUploadResponse();

        response.setFileId("file_001");
        assertEquals("file_001", response.getFileId());

        response.setUrl("http://localhost:8080/files/2024/01/01/test.jpg");
        assertEquals("http://localhost:8080/files/2024/01/01/test.jpg", response.getUrl());

        response.setThumbnailUrl("http://localhost:8080/files/2024/01/01/test_thumb.jpg");
        assertEquals("http://localhost:8080/files/2024/01/01/test_thumb.jpg", response.getThumbnailUrl());

        response.setWidth(1920);
        assertEquals(1920, response.getWidth());

        response.setHeight(1080);
        assertEquals(1080, response.getHeight());

        response.setSize(1024000L);
        assertEquals(1024000L, response.getSize());

        response.setFileType("image/jpeg");
        assertEquals("image/jpeg", response.getFileType());
    }

    @Test
    @DisplayName("equals 和 hashCode 测试")
    void testEqualsHashCode() {
        final ImageUploadResponse response1 = new ImageUploadResponse();
        response1.setFileId("file_001");
        response1.setUrl("http://localhost:8080/files/test.jpg");
        response1.setWidth(1920);
        response1.setHeight(1080);

        final ImageUploadResponse response2 = new ImageUploadResponse();
        response2.setFileId("file_001");
        response2.setUrl("http://localhost:8080/files/test.jpg");
        response2.setWidth(1920);
        response2.setHeight(1080);

        final ImageUploadResponse response3 = new ImageUploadResponse();
        response3.setFileId("file_002");
        response3.setUrl("http://localhost:8080/files/test2.jpg");

        // 测试 equals
        assertEquals(response1, response2);
        assertNotEquals(response1, response3);
        assertNotEquals(response1, null);
        assertNotEquals(response1, "string");

        // 测试 hashCode
        assertEquals(response1.hashCode(), response2.hashCode());
        assertNotEquals(response1.hashCode(), response3.hashCode());
    }

    @Test
    @DisplayName("toString 测试")
    void testToString() {
        final ImageUploadResponse response = new ImageUploadResponse();
        response.setFileId("file_001");
        response.setUrl("http://localhost:8080/files/test.jpg");
        response.setWidth(1920);
        response.setHeight(1080);

        final String str = response.toString();
        assertNotNull(str);
        assertTrue(str.contains("file_001"));
        assertTrue(str.contains("http://localhost:8080/files/test.jpg"));
        assertTrue(str.contains("1920"));
        assertTrue(str.contains("1080"));
    }

    @Test
    @DisplayName("完整数据测试")
    void testFullData() {
        final ImageUploadResponse response = new ImageUploadResponse();
        response.setFileId("abc123");
        response.setUrl("http://localhost:8080/files/2024/06/22/abc123.jpg");
        response.setThumbnailUrl("http://localhost:8080/files/2024/06/22/abc123_thumb.jpg");
        response.setWidth(1920);
        response.setHeight(1080);
        response.setSize(2048000L);
        response.setFileType("image/jpeg");

        // 验证所有字段
        assertNotNull(response.getFileId());
        assertNotNull(response.getUrl());
        assertNotNull(response.getThumbnailUrl());
        assertNotNull(response.getWidth());
        assertNotNull(response.getHeight());
        assertNotNull(response.getSize());
        assertNotNull(response.getFileType());

        // 验证数据一致性
        assertTrue(response.getWidth() > 0);
        assertTrue(response.getHeight() > 0);
        assertTrue(response.getSize() > 0);
        assertTrue(response.getUrl().contains(response.getFileId()));
        assertTrue(response.getThumbnailUrl().contains("_thumb"));
    }

    @Test
    @DisplayName("空值测试")
    void testNullValues() {
        final ImageUploadResponse response = new ImageUploadResponse();

        // 所有字段初始为 null
        assertNull(response.getFileId());
        assertNull(response.getUrl());
        assertNull(response.getThumbnailUrl());
        assertNull(response.getWidth());
        assertNull(response.getHeight());
        assertNull(response.getSize());
        assertNull(response.getFileType());

        // 设置 null 值
        response.setFileId(null);
        response.setUrl(null);
        assertNull(response.getFileId());
        assertNull(response.getUrl());
    }
}