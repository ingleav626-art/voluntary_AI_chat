package org.example.client.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.example.client.config.ClientConfig;
import org.example.client.model.*;
import org.example.client.util.TokenStorage;
import org.junit.jupiter.api.*;

import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChatService HTTP 集成测试")
class ChatServiceHttpTest {

    private static HttpServer mockServer;
    private static final int MOCK_PORT = 18082;
    private static String originalBaseUrl;

    @BeforeAll
    static void setUp() throws Exception {
        // 保存原始 baseUrl
        originalBaseUrl = ClientConfig.getInstance().getBaseUrl();

        mockServer = HttpServer.create(new InetSocketAddress(MOCK_PORT), 0);
        mockServer.setExecutor(null);

        // --- 会话列表 ---
        mockServer.createContext("/api/conversation/list", exchange -> {
            sendJson(exchange, 200,
                    "{\"code\":200,\"message\":\"success\",\"data\":{"
                    + "\"list\":[{\"sessionId\":\"p_1001_1002\",\"targetId\":1002,"
                    + "\"targetName\":\"李四\",\"unreadCount\":0}],"
                    + "\"total\":1,\"page\":1,\"size\":20}}");
        });

        // --- 聊天记录 ---
        mockServer.createContext("/api/message/history", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            if (query != null && query.contains("sessionId=401")) {
                sendJson(exchange, 401, "{\"code\":401,\"message\":\"未认证\",\"data\":null}");
            } else if (query != null && query.contains("sessionId=403")) {
                sendJson(exchange, 403, "{\"code\":403,\"message\":\"无权限\",\"data\":null}");
            } else {
                sendJson(exchange, 200,
                        "{\"code\":200,\"message\":\"success\",\"data\":{"
                        + "\"list\":[{\"messageId\":1,\"sessionId\":\"p_1001_1002\","
                        + "\"content\":\"你好\",\"type\":\"TEXT\"}],"
                        + "\"total\":1,\"page\":1,\"size\":20}}");
            }
        });

        // --- 发送消息 ---
        mockServer.createContext("/api/message/send", exchange -> {
            sendJson(exchange, 200,
                    "{\"code\":200,\"message\":\"发送成功\",\"data\":{"
                    + "\"messageId\":100,\"createTime\":\"2024-01-01T10:00:00\"}}");
        });

        // --- 已读上报 ---
        mockServer.createContext("/api/message/read", exchange -> {
            sendJson(exchange, 200,
                    "{\"code\":200,\"message\":\"success\",\"data\":null}");
        });

        // --- 撤回消息 ---
        mockServer.createContext("/api/message/recall", exchange -> {
            String body = readBody(exchange);
            if (body != null && body.contains("\"messageId\":\"500\"")) {
                sendJson(exchange, 500, "{\"code\":500,\"message\":\"服务器内部错误\",\"data\":null}");
            } else {
                sendJson(exchange, 200,
                    "{\"code\":200,\"message\":\"已撤回\",\"data\":{"
                    + "\"messageId\":1,\"sessionId\":\"p_1001_1002\",\"senderId\":1001}}");
            }
        });

        // --- 上传图片 (模拟 multipart) ---
        mockServer.createContext("/api/message/upload/image", exchange -> {
            sendJson(exchange, 200,
                    "{\"code\":200,\"message\":\"上传成功\",\"data\":{"
                    + "\"url\":\"http://example.com/img.jpg\","
                    + "\"thumbnailUrl\":\"http://example.com/thumb.jpg\","
                    + "\"width\":800,\"height\":600}}");
        });

        mockServer.start();

        // 修改 ClientConfig 指向 mock server
        setBaseUrl("http://localhost:" + MOCK_PORT + "/api");
    }

    @BeforeEach
    void setToken() {
        LoginResponse login = new LoginResponse();
        login.setAccessToken("mock-token");
        login.setRefreshToken("mock-refresh");
        TokenStorage.save(login, false);
    }

    @AfterEach
    void clearToken() {
        TokenStorage.clear();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (mockServer != null) mockServer.stop(0);
        setBaseUrl(originalBaseUrl);
    }

    private static void setBaseUrl(String url) throws Exception {
        Field field = ClientConfig.class.getDeclaredField("baseUrl");
        field.setAccessible(true);
        field.set(ClientConfig.getInstance(), url);
    }

    private static void sendJson(HttpExchange ex, int status, String body) {
        try {
            ex.getResponseHeaders().set("Content-Type", "application/json;charset=utf-8");
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, b.length);
            OutputStream os = ex.getResponseBody();
            os.write(b);
            os.close();
        } catch (Exception ignored) {
        }
    }

    private static String readBody(HttpExchange ex) {
        try {
            return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    // ============ getConversations ============

    @Test
    @DisplayName("getConversations(分页) - 成功")
    void getConversationsPaged_success() throws Exception {
        var resp = ChatService.getInstance().getConversations(1, 20).get(5, TimeUnit.SECONDS);
        assertTrue(resp.isSuccess());
        assertEquals(1, resp.getData().getList().size());
    }

    @Test
    @DisplayName("getConversations(无参) - 成功")
    void getConversationsDefault_success() throws Exception {
        var resp = ChatService.getInstance().getConversations().get(5, TimeUnit.SECONDS);
        assertTrue(resp.isSuccess());
    }

    @Test
    @DisplayName("getConversations(keyword) - 成功")
    void getConversationsKeyword_success() throws Exception {
        var resp = ChatService.getInstance().getConversations("李").get(5, TimeUnit.SECONDS);
        assertTrue(resp.isSuccess());
    }

    // ============ getHistory ============

    @Test
    @DisplayName("getHistory - 成功")
    void getHistory_success() throws Exception {
        var resp = ChatService.getInstance().getHistory("p_1001_1002", 1, 20).get(5, TimeUnit.SECONDS);
        assertTrue(resp.isSuccess());
    }

    @Test
    @DisplayName("getHistory - 401 响应")
    void getHistory_401() throws Exception {
        var resp = ChatService.getInstance().getHistory("401_session", 1, 20).get(5, TimeUnit.SECONDS);
        assertEquals(401, resp.getCode());
    }

    @Test
    @DisplayName("getHistory - 403 响应")
    void getHistory_403() throws Exception {
        var resp = ChatService.getInstance().getHistory("403_session", 1, 20).get(5, TimeUnit.SECONDS);
        assertEquals(403, resp.getCode());
    }

    // ============ sendMessage ============

    @Test
    @DisplayName("sendMessage - 成功")
    void sendMessage_success() throws Exception {
        SendMessageRequest req = new SendMessageRequest("p_1001_1002", "TEXT", "你好");
        var resp = ChatService.getInstance().sendMessage(req).get(5, TimeUnit.SECONDS);
        assertTrue(resp.isSuccess());
    }

    // ============ markRead ============

    @Test
    @DisplayName("markRead - 成功")
    void markRead_success() throws Exception {
        MarkReadRequest req = new MarkReadRequest("p_1001_1002", List.of(1L, 2L));
        var resp = ChatService.getInstance().markRead(req).get(5, TimeUnit.SECONDS);
        assertTrue(resp.isSuccess());
    }

    // ============ recallMessage ============

    @Test
    @DisplayName("recallMessage - 成功")
    void recallMessage_success() throws Exception {
        var resp = ChatService.getInstance().recallMessage(1L).get(5, TimeUnit.SECONDS);
        assertTrue(resp.isSuccess());
    }

    @Test
    @DisplayName("recallMessage - HTTP 500")
    void recallMessage_http500() throws Exception {
        var resp = ChatService.getInstance().recallMessage(500L).get(5, TimeUnit.SECONDS);
        assertFalse(resp.isSuccess());
    }

    // ============ loadImageBytes ============

    @Test
    @DisplayName("loadImageBytes - 成功")
    void loadImageBytes_success() throws Exception {
        var future = ChatService.getInstance().loadImageBytes(
                "http://localhost:" + MOCK_PORT + "/api/conversation/list");
        byte[] data = future.get(5, TimeUnit.SECONDS);
        assertNotNull(data);
        assertTrue(data.length > 0);
    }
}
