package org.example.client.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import org.example.client.model.ApiResponse;
import org.example.client.model.ConversationInfo;
import org.example.client.model.FileUploadResponse;
import org.example.client.model.ImageUploadResponse;
import org.example.client.model.MarkReadRequest;
import org.example.client.model.MessageInfo;
import org.example.client.model.PageResult;
import org.example.client.model.RecallMessageResponse;
import org.example.client.model.SendMessageRequest;
import org.example.client.model.SendMessageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChatService 单元测试
 *
 * <p>
 * 测试单例模式、方法返回值非空、参数边界等行为。
 * HTTP 请求部分需要集成测试或 Mock。
 * </p>
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@DisplayName("ChatService 测试")
class ChatServiceTest {

    @Test
    @DisplayName("获取单例实例 - 同一实例")
    void testGetInstance() {
        final ChatService instance1 = ChatService.getInstance();
        final ChatService instance2 = ChatService.getInstance();

        assertNotNull(instance1);
        assertSame(instance1, instance2, "单例应返回同一实例");
    }

    @Test
    @DisplayName("getConversations(分页) 返回非空 CompletableFuture")
    void testGetConversationsPagedReturnsFuture() {
        final ChatService chatService = ChatService.getInstance();
        final CompletableFuture<ApiResponse<PageResult<ConversationInfo>>> future = chatService.getConversations(1, 20);

        assertNotNull(future, "getConversations 应返回非空 Future");
    }

    @Test
    @DisplayName("getConversations(无参) 默认分页返回非空 Future")
    void testGetConversationsDefaultReturnsFuture() {
        final ChatService chatService = ChatService.getInstance();
        final CompletableFuture<ApiResponse<PageResult<ConversationInfo>>> future = chatService.getConversations();

        assertNotNull(future, "getConversations 默认分页应返回非空 Future");
    }

    @Test
    @DisplayName("getHistory 返回非空 CompletableFuture")
    void testGetHistoryReturnsFuture() {
        final ChatService chatService = ChatService.getInstance();
        final CompletableFuture<ApiResponse<PageResult<MessageInfo>>> future = chatService.getHistory("p_1001_1002", 1,
                20);

        assertNotNull(future, "getHistory 应返回非空 Future");
    }

    @Test
    @DisplayName("getHistory null sessionId 不抛异常")
    void testGetHistoryNullSessionId() {
        final ChatService chatService = ChatService.getInstance();
        assertDoesNotThrow(() -> chatService.getHistory(null, 1, 20));
    }

    @Test
    @DisplayName("sendMessage 返回非空 CompletableFuture")
    void testSendMessageReturnsFuture() {
        final ChatService chatService = ChatService.getInstance();
        final SendMessageRequest request = new SendMessageRequest("p_1001_1002", "TEXT", "你好");

        final CompletableFuture<ApiResponse<SendMessageResponse>> future = chatService.sendMessage(request);

        assertNotNull(future, "sendMessage 应返回非空 Future");
    }

    @Test
    @DisplayName("markRead 正常参数返回非空 Future")
    void testMarkReadNormal() {
        final ChatService chatService = ChatService.getInstance();
        final MarkReadRequest request = new MarkReadRequest(
                "p_1001_1002", Arrays.asList(1L, 2L, 3L));

        final CompletableFuture<ApiResponse<Void>> future = chatService.markRead(request);

        assertNotNull(future, "markRead 应返回非空 Future");
    }

    @Test
    @DisplayName("markRead null 消息列表不抛异常")
    void testMarkReadNullMessageIds() {
        final ChatService chatService = ChatService.getInstance();
        final MarkReadRequest request = new MarkReadRequest("p_1001_1002", null);

        assertDoesNotThrow(() -> chatService.markRead(request));
    }

    @Test
    @DisplayName("recallMessage 返回非空 CompletableFuture")
    void testRecallMessageReturnsFuture() {
        final ChatService chatService = ChatService.getInstance();
        final CompletableFuture<ApiResponse<RecallMessageResponse>> future = chatService.recallMessage(1001L);

        assertNotNull(future, "recallMessage 应返回非空 Future");
    }

    @Test
    @DisplayName("recallMessage null ID 不抛异常")
    void testRecallMessageNullId() {
        final ChatService chatService = ChatService.getInstance();
        assertDoesNotThrow(() -> chatService.recallMessage(null));
    }

    // ==================== 图片上传测试 ====================

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("uploadImage 正常文件返回非空 Future")
    void testUploadImageNormalFile() throws Exception {
        final ChatService chatService = ChatService.getInstance();

        // 创建临时图片文件
        final Path imagePath = tempDir.resolve("test.jpg");
        Files.write(imagePath, new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0 }); // JPEG 文件头

        final CompletableFuture<ApiResponse<ImageUploadResponse>> future = chatService.uploadImage(imagePath);

        assertNotNull(future, "uploadImage 应返回非空 Future");
    }

    @Test
    @DisplayName("uploadImage 空文件路径不抛异常")
    void testUploadImageEmptyPath() {
        final ChatService chatService = ChatService.getInstance();
        assertDoesNotThrow(() -> chatService.uploadImage(Path.of("")));
    }

    @Test
    @DisplayName("uploadImage 不存在的文件路径不抛异常")
    void testUploadImageNonExistentFile() {
        final ChatService chatService = ChatService.getInstance();
        final Path nonExistentPath = Path.of("/non/existent/file.jpg");

        // 不存在的文件会在读取时抛出异常，但方法本身不抛异常
        assertDoesNotThrow(() -> chatService.uploadImage(nonExistentPath));
    }

    @Test
    @DisplayName("uploadImage PNG 文件返回非空 Future")
    void testUploadImagePngFile() throws Exception {
        final ChatService chatService = ChatService.getInstance();

        // 创建临时 PNG 文件
        final Path imagePath = tempDir.resolve("test.png");
        Files.write(imagePath, new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47 }); // PNG 文件头

        final CompletableFuture<ApiResponse<ImageUploadResponse>> future = chatService.uploadImage(imagePath);

        assertNotNull(future, "uploadImage PNG 文件应返回非空 Future");
    }

    @Test
    @DisplayName("uploadImage GIF 文件返回非空 Future")
    void testUploadImageGifFile() throws Exception {
        final ChatService chatService = ChatService.getInstance();

        // 创建临时 GIF 文件
        final Path imagePath = tempDir.resolve("test.gif");
        Files.write(imagePath, new byte[] { 0x47, 0x49, 0x46, 0x38 }); // GIF 文件头

        final CompletableFuture<ApiResponse<ImageUploadResponse>> future = chatService.uploadImage(imagePath);

        assertNotNull(future, "uploadImage GIF 文件应返回非空 Future");
    }

    @Test
    @DisplayName("uploadImage WebP 文件返回非空 Future")
    void testUploadImageWebPFile() throws Exception {
        final ChatService chatService = ChatService.getInstance();

        // 创建临时 WebP 文件
        final Path imagePath = tempDir.resolve("test.webp");
        Files.write(imagePath, new byte[] { 0x52, 0x49, 0x46, 0x46 }); // RIFF 文件头（WebP）

        final CompletableFuture<ApiResponse<ImageUploadResponse>> future = chatService.uploadImage(imagePath);

        assertNotNull(future, "uploadImage WebP 文件应返回非空 Future");
    }

    @Test
    @DisplayName("uploadImage 大文件（超过10MB）返回非空 Future")
    void testUploadImageLargeFile() throws Exception {
        final ChatService chatService = ChatService.getInstance();

        // 创建超过10MB的临时文件
        final Path largeImagePath = tempDir.resolve("large.jpg");
        final byte[] largeData = new byte[11 * 1024 * 1024]; // 11MB
        Files.write(largeImagePath, largeData);

        final CompletableFuture<ApiResponse<ImageUploadResponse>> future = chatService.uploadImage(largeImagePath);

        assertNotNull(future, "uploadImage 大文件应返回非空 Future");
        // 注意：实际响应会在服务端校验失败，但客户端方法本身不抛异常
    }

    // ==================== 更多边界测试 ====================

    @Test
    @DisplayName("getHistory 负页码应不抛异常")
    void testGetHistoryNegativePage() {
        final ChatService chatService = ChatService.getInstance();
        assertDoesNotThrow(() -> chatService.getHistory("p_1001_1002", -1, 20));
    }

    @Test
    @DisplayName("getHistory 零大小应不抛异常")
    void testGetHistoryZeroSize() {
        final ChatService chatService = ChatService.getInstance();
        assertDoesNotThrow(() -> chatService.getHistory("p_1001_1002", 1, 0));
    }

    @Test
    @DisplayName("sendMessage null sessionId 不抛异常")
    void testSendMessageNullSessionId() {
        final ChatService chatService = ChatService.getInstance();
        final SendMessageRequest request = new SendMessageRequest(null, "TEXT", "你好");
        final CompletableFuture<ApiResponse<SendMessageResponse>> future = chatService.sendMessage(request);
        assertNotNull(future);
    }

    @Test
    @DisplayName("sendMessage null msgType 不抛异常")
    void testSendMessageNullMsgType() {
        final ChatService chatService = ChatService.getInstance();
        final SendMessageRequest request = new SendMessageRequest("p_1001_1002", null, "你好");
        final CompletableFuture<ApiResponse<SendMessageResponse>> future = chatService.sendMessage(request);
        assertNotNull(future);
    }

    @Test
    @DisplayName("sendMessage null content 不抛异常")
    void testSendMessageNullContent() {
        final ChatService chatService = ChatService.getInstance();
        final SendMessageRequest request = new SendMessageRequest("p_1001_1002", "TEXT", null);
        final CompletableFuture<ApiResponse<SendMessageResponse>> future = chatService.sendMessage(request);
        assertNotNull(future);
    }

    @Test
    @DisplayName("markRead null sessionId 不抛异常")
    void testMarkReadNullSessionId() {
        final ChatService chatService = ChatService.getInstance();
        final MarkReadRequest request = new MarkReadRequest(null, Arrays.asList(1L));
        final CompletableFuture<ApiResponse<Void>> future = chatService.markRead(request);
        assertNotNull(future);
    }

    @Test
    @DisplayName("markRead 空消息列表不抛异常")
    void testMarkReadEmptyMessageIds() {
        final ChatService chatService = ChatService.getInstance();
        final MarkReadRequest request = new MarkReadRequest("p_1001_1002", Arrays.asList());
        assertDoesNotThrow(() -> chatService.markRead(request));
    }

    @Test
    @DisplayName("getConversations 负页码不抛异常")
    void testGetConversationsNegativePage() {
        final ChatService chatService = ChatService.getInstance();
        assertDoesNotThrow(() -> chatService.getConversations(-1, 20));
    }

    @Test
    @DisplayName("getConversations 零大小不抛异常")
    void testGetConversationsZeroSize() {
        final ChatService chatService = ChatService.getInstance();
        assertDoesNotThrow(() -> chatService.getConversations(1, 0));
    }

    @Test
    @DisplayName("uploadImage BMP 文件不抛异常")
    void testUploadImageBmpFile() throws Exception {
        final ChatService chatService = ChatService.getInstance();
        final Path imagePath = tempDir.resolve("test.bmp");
        Files.write(imagePath, new byte[] { 0x42, 0x4D }); // BMP 文件头
        assertDoesNotThrow(() -> chatService.uploadImage(imagePath));
    }

    @Test
    @DisplayName("recallMessage 零 ID 不抛异常")
    void testRecallMessageZeroId() {
        final ChatService chatService = ChatService.getInstance();
        assertDoesNotThrow(() -> chatService.recallMessage(0L));
    }

    // ==================== 文件上传测试 ====================

    @Test
    @DisplayName("uploadFile 正常文件返回非空 Future")
    void testUploadFileNormalFile() throws Exception {
        final ChatService chatService = ChatService.getInstance();
        final Path filePath = tempDir.resolve("test.txt");
        Files.write(filePath, "Hello, World!".getBytes());

        final CompletableFuture<ApiResponse<FileUploadResponse>> future = chatService.uploadFile(filePath);

        assertNotNull(future, "uploadFile 应返回非空 Future");
    }

    @Test
    @DisplayName("uploadFile 空路径不抛异常")
    void testUploadFileEmptyPath() {
        final ChatService chatService = ChatService.getInstance();
        assertDoesNotThrow(() -> chatService.uploadFile(Path.of("")));
    }

    @Test
    @DisplayName("uploadFile 不存在的文件不抛异常")
    void testUploadFileNonExistentFile() {
        final ChatService chatService = ChatService.getInstance();
        assertDoesNotThrow(() -> chatService.uploadFile(Path.of("/non/existent/file.pdf")));
    }

    @Test
    @DisplayName("uploadFile 大文件返回非空 Future")
    void testUploadFileLargeFile() throws Exception {
        final ChatService chatService = ChatService.getInstance();
        final Path largeFilePath = tempDir.resolve("large.bin");
        final byte[] largeData = new byte[50 * 1024 * 1024]; // 50MB
        Files.write(largeFilePath, largeData);

        final CompletableFuture<ApiResponse<FileUploadResponse>> future = chatService.uploadFile(largeFilePath);

        assertNotNull(future, "uploadFile 大文件应返回非空 Future");
    }

    @Test
    @DisplayName("uploadFile 二进制文件返回非空 Future")
    void testUploadFileBinaryFile() throws Exception {
        final ChatService chatService = ChatService.getInstance();
        final Path binPath = tempDir.resolve("data.bin");
        Files.write(binPath, new byte[] { 0x00, 0x01, 0x02, 0x03, (byte) 0xFF });

        final CompletableFuture<ApiResponse<FileUploadResponse>> future = chatService.uploadFile(binPath);

        assertNotNull(future, "uploadFile 二进制文件应返回非空 Future");
    }

    @Test
    @DisplayName("uploadFile 中文文件名不抛异常")
    void testUploadFileChineseName() throws Exception {
        final ChatService chatService = ChatService.getInstance();
        final Path filePath = tempDir.resolve("报告.pdf");
        Files.write(filePath, "报告内容".getBytes());

        assertDoesNotThrow(() -> chatService.uploadFile(filePath));
    }

    // ==================== 文件下载测试 ====================

    @Test
    @DisplayName("loadFileBytes 完整 URL 返回非空 Future")
    void testLoadFileBytesFullUrl() {
        final ChatService chatService = ChatService.getInstance();
        final CompletableFuture<byte[]> future = chatService.loadFileBytes(
                "http://localhost:8080/chat-files/2026/07/01/test.pdf");

        assertNotNull(future, "loadFileBytes 应返回非空 Future");
    }

    @Test
    @DisplayName("loadFileBytes 相对路径返回非空 Future")
    void testLoadFileBytesRelativeUrl() {
        final ChatService chatService = ChatService.getInstance();
        final CompletableFuture<byte[]> future = chatService.loadFileBytes(
                "/chat-files/2026/07/01/test.pdf");

        assertNotNull(future, "loadFileBytes 相对路径应返回非空 Future");
    }

    @Test
    @DisplayName("loadFileBytes null URL 不抛异常")
    void testLoadFileBytesNullUrl() {
        final ChatService chatService = ChatService.getInstance();
        assertDoesNotThrow(() -> chatService.loadFileBytes(null));
    }

    @Test
    @DisplayName("loadFileBytes 空 URL 不抛异常")
    void testLoadFileBytesEmptyUrl() {
        final ChatService chatService = ChatService.getInstance();
        assertDoesNotThrow(() -> chatService.loadFileBytes(""));
    }

    // ==================== loadImageBytes 补充测试 ====================

    @Test
    @DisplayName("loadImageBytes 相对路径返回非空 Future")
    void testLoadImageBytesRelativeUrl() {
        final ChatService chatService = ChatService.getInstance();
        final CompletableFuture<byte[]> future = chatService.loadImageBytes(
                "/files/2026/07/01/photo.jpg");

        assertNotNull(future, "loadImageBytes 相对路径应返回非空 Future");
    }

    @Test
    @DisplayName("loadImageBytes null URL 不抛异常")
    void testLoadImageBytesNullUrl() {
        final ChatService chatService = ChatService.getInstance();
        assertDoesNotThrow(() -> chatService.loadImageBytes(null));
    }
}
