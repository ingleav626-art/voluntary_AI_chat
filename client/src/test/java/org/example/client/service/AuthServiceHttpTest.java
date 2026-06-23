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
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AuthService HTTP 集成测试")
class AuthServiceHttpTest {

    private static HttpServer mockServer;
    private static final int MOCK_PORT = 18081;

    @BeforeAll
    static void setUp() throws Exception {
        mockServer = HttpServer.create(new InetSocketAddress(MOCK_PORT), 0);
        mockServer.setExecutor(null);

        // 发送验证码 - 根据参数决定返回状态
        mockServer.createContext("/api/auth/sms/send", exchange -> {
            String body = readBody(exchange);
            if (body.contains("\"phone\":\"400-test\"")) {
                sendJson(exchange, 400, "{\"code\":400,\"message\":\"参数错误\",\"data\":null}");
            } else if (body.contains("\"phone\":\"500-test\"")) {
                sendJson(exchange, 500, "{\"code\":500,\"message\":\"服务器错误\",\"data\":null}");
            } else if (body.contains("\"phone\":\"nil-test\"")) {
                sendJson(exchange, 200, "{\"code\":200,\"message\":null,\"data\":null}");
            } else if (body.contains("\"phone\":\"13800138000\"")) {
                sendJson(exchange, 200, "{\"code\":200,\"message\":\"SMS_OK\",\"data\":null}");
            } else {
                sendJson(exchange, 200, "{\"code\":400,\"message\":\"BAD_PHONE\",\"data\":null}");
            }
        });

        // 注册
        mockServer.createContext("/api/auth/register", exchange -> {
            String body = readBody(exchange);
            if (body.contains("\"code\":\"success\"")) {
                sendJson(exchange, 200, "{\"code\":200,\"message\":\"OK\",\"data\":{"
                        + "\"accessToken\":\"mock-access\",\"refreshToken\":\"mock-refresh\",\"expiresIn\":7200,"
                        + "\"user\":{\"userId\":1,\"phone\":\"138****8001\",\"username\":\"testuser\"}}}");
            } else if (body.contains("\"code\":\"dup-phone\"")) {
                sendJson(exchange, 200, "{\"code\":1001,\"message\":\"PHONE_EXISTS\",\"data\":null}");
            } else if (body.contains("\"code\":\"dup-user\"")) {
                sendJson(exchange, 200, "{\"code\":1003,\"message\":\"USERNAME_EXISTS\",\"data\":null}");
            } else if (body.contains("\"code\":\"server-error\"")) {
                sendJson(exchange, 200, "{\"code\":500,\"message\":\"SERVER_ERR\",\"data\":null}");
            } else if (body.contains("\"code\":\"http400\"")) {
                sendJson(exchange, 400, "{\"code\":400,\"message\":\"BAD_REQ\",\"data\":null}");
            } else if (body.contains("\"code\":\"http500\"")) {
                sendJson(exchange, 500, "{\"code\":500,\"message\":\"ERR\",\"data\":null}");
            } else {
                sendJson(exchange, 200, "{\"code\":1002,\"message\":\"CODE_ERROR\",\"data\":null}");
            }
        });

        // 登录
        mockServer.createContext("/api/auth/login", exchange -> {
            String body = readBody(exchange);
            if (body.contains("\"password\":\"correct\"")) {
                sendJson(exchange, 200, "{\"code\":200,\"message\":\"OK\",\"data\":{"
                        + "\"accessToken\":\"mock-access\",\"refreshToken\":\"mock-refresh\",\"expiresIn\":7200,"
                        + "\"user\":{\"userId\":1,\"phone\":\"138****8000\",\"username\":\"testuser\"}}}");
            } else if (body.contains("\"password\":\"wrong\"")) {
                sendJson(exchange, 200, "{\"code\":1004,\"message\":\"WRONG_CRED\",\"data\":null}");
            } else if (body.contains("\"password\":\"locked\"")) {
                sendJson(exchange, 200, "{\"code\":1005,\"message\":\"LOCKED\",\"data\":null}");
            } else if (body.contains("\"password\":\"http400\"")) {
                sendJson(exchange, 400, "{\"code\":400,\"message\":\"BAD_REQ\",\"data\":null}");
            } else if (body.contains("\"password\":\"http500\"")) {
                sendJson(exchange, 500, "{\"code\":500,\"message\":\"ERR\",\"data\":null}");
            } else {
                sendJson(exchange, 200, "{\"code\":400,\"message\":\"BAD_REQ\",\"data\":null}");
            }
        });

        // 忘记密码
        mockServer.createContext("/api/auth/forgot-password", exchange -> {
            String body = readBody(exchange);
            if (body.contains("\"code\":\"123456\"")) {
                sendJson(exchange, 200, "{\"code\":200,\"message\":\"密码重置成功\",\"data\":null}");
            } else if (body.contains("\"code\":\"expired\"")) {
                sendJson(exchange, 200, "{\"code\":1002,\"message\":\"CODE_EXPIRED\",\"data\":null}");
            } else if (body.contains("\"code\":\"http400\"")) {
                sendJson(exchange, 400, "{\"code\":400,\"message\":\"BAD_REQ\",\"data\":null}");
            } else if (body.contains("\"code\":\"http500\"")) {
                sendJson(exchange, 500, "{\"code\":500,\"message\":\"ERR\",\"data\":null}");
            } else {
                sendJson(exchange, 200, "{\"code\":400,\"message\":\"UNKNOWN\",\"data\":null}");
            }
        });

        mockServer.start();

        // 用反射修改 ClientConfig 的 currentBaseUrl
        Field field = ClientConfig.class.getDeclaredField("currentBaseUrl");
        field.setAccessible(true);
        field.set(ClientConfig.getInstance(), "http://localhost:" + MOCK_PORT + "/api");
    }

    @AfterAll
    static void tearDown() {
        if (mockServer != null)
            mockServer.stop(0);
    }

    // ============ 工具方法 ============

    private static String readBody(HttpExchange ex) {
        try {
            return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
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

    // ============ 正向测试 ============

    @Test
    @DisplayName("发送验证码 - 成功")
    void sendSmsCode_success() throws Exception {
        var resp = AuthService.getInstance().sendSmsCode(new SmsSendRequest("13800138000")).get(5, TimeUnit.SECONDS);
        assertTrue(resp.isSuccess());
        assertEquals("SMS_OK", resp.getMessage());
    }

    @Test
    @DisplayName("注册 - 成功")
    void register_success() throws Exception {
        var resp = AuthService.getInstance().register(new RegisterRequest("13800138001", "success", "张三", "pass"))
                .get(5, TimeUnit.SECONDS);
        assertTrue(resp.isSuccess());
        assertEquals("mock-access", resp.getData().getAccessToken());
    }

    @Test
    @DisplayName("注册 - 验证码错误")
    void register_wrongCode() throws Exception {
        var resp = AuthService.getInstance().register(new RegisterRequest("13800138001", "000000", "张三", "pass")).get(5,
                TimeUnit.SECONDS);
        assertFalse(resp.isSuccess());
        // Mock服务器返回CODE_ERROR，但AuthService可能转换为中文消息
        assertTrue(resp.getMessage().contains("CODE_ERROR") || resp.getMessage().contains("验证码错误"));
    }

    @Test
    @DisplayName("登录 - 成功")
    void login_success() throws Exception {
        var resp = AuthService.getInstance().login(new LoginRequest("13800138000", "correct", false)).get(5,
                TimeUnit.SECONDS);
        assertTrue(resp.isSuccess());
        assertEquals("mock-access", resp.getData().getAccessToken());
    }

    @Test
    @DisplayName("获取单例实例")
    void getInstance() {
        assertNotNull(AuthService.getInstance());
        assertSame(AuthService.getInstance(), AuthService.getInstance());
    }

    // ============ 业务异常路径 ============

    @Test
    @DisplayName("登录 - 错误凭证 1004")
    void login_wrongCredentials() throws Exception {
        var resp = AuthService.getInstance().login(new LoginRequest("13800138000", "wrong", false)).get(5,
                TimeUnit.SECONDS);
        assertEquals(1004, resp.getCode());
    }

    @Test
    @DisplayName("登录 - 账号锁定 1005")
    void login_accountLocked() throws Exception {
        var resp = AuthService.getInstance().login(new LoginRequest("13800138000", "locked", false)).get(5,
                TimeUnit.SECONDS);
        assertEquals(1005, resp.getCode());
    }

    @Test
    @DisplayName("注册 - 手机号已注册 1001")
    void register_phoneExists() throws Exception {
        var resp = AuthService.getInstance().register(new RegisterRequest("13800138001", "dup-phone", "张三", "pass"))
                .get(5, TimeUnit.SECONDS);
        assertEquals(1001, resp.getCode());
    }

    @Test
    @DisplayName("注册 - 用户名已存在 1003")
    void register_usernameExists() throws Exception {
        var resp = AuthService.getInstance().register(new RegisterRequest("13800138001", "dup-user", "张三", "pass"))
                .get(5, TimeUnit.SECONDS);
        assertEquals(1003, resp.getCode());
    }

    // ============ HTTP 错误路径 ============

    @Test
    @DisplayName("登录 - HTTP 400")
    void login_http400() throws Exception {
        var resp = AuthService.getInstance().login(new LoginRequest("13800138000", "http400", false)).get(5,
                TimeUnit.SECONDS);
        assertEquals(400, resp.getCode());
    }

    @Test
    @DisplayName("登录 - HTTP 500")
    void login_http500() throws Exception {
        var resp = AuthService.getInstance().login(new LoginRequest("13800138000", "http500", false)).get(5,
                TimeUnit.SECONDS);
        assertEquals(500, resp.getCode());
    }

    @Test
    @DisplayName("注册 - HTTP 400")
    void register_http400() throws Exception {
        var resp = AuthService.getInstance().register(new RegisterRequest("13800138001", "http400", "张三", "pass"))
                .get(5, TimeUnit.SECONDS);
        assertEquals(400, resp.getCode());
    }

    @Test
    @DisplayName("注册 - HTTP 500")
    void register_http500() throws Exception {
        var resp = AuthService.getInstance().register(new RegisterRequest("13800138001", "http500", "张三", "pass"))
                .get(5, TimeUnit.SECONDS);
        assertEquals(500, resp.getCode());
    }

    @Test
    @DisplayName("发送验证码 - HTTP 400")
    void sendSms_http400() throws Exception {
        var resp = AuthService.getInstance().sendSmsCode(new SmsSendRequest("400-test")).get(5, TimeUnit.SECONDS);
        assertEquals(400, resp.getCode());
    }

    @Test
    @DisplayName("发送验证码 - HTTP 500")
    void sendSms_http500() throws Exception {
        var resp = AuthService.getInstance().sendSmsCode(new SmsSendRequest("500-test")).get(5, TimeUnit.SECONDS);
        assertEquals(500, resp.getCode());
    }

    // ============ 忘记密码 ============

    @Test
    @DisplayName("忘记密码 - 成功")
    void forgotPassword_success() throws Exception {
        var resp = AuthService.getInstance().forgotPassword(
                new ForgotPasswordRequest("13800138000", "123456", "newpass", "newpass")).get(5, TimeUnit.SECONDS);
        assertTrue(resp.isSuccess());
    }

    @Test
    @DisplayName("忘记密码 - 验证码过期 1002")
    void forgotPassword_codeExpired() throws Exception {
        var resp = AuthService.getInstance().forgotPassword(
                new ForgotPasswordRequest("13800138000", "expired", "newpass", "newpass")).get(5, TimeUnit.SECONDS);
        assertEquals(1002, resp.getCode());
    }

    @Test
    @DisplayName("忘记密码 - HTTP 400")
    void forgotPassword_http400() throws Exception {
        var resp = AuthService.getInstance().forgotPassword(
                new ForgotPasswordRequest("13800138000", "http400", "newpass", "newpass")).get(5, TimeUnit.SECONDS);
        assertEquals(400, resp.getCode());
    }

    @Test
    @DisplayName("忘记密码 - HTTP 500")
    void forgotPassword_http500() throws Exception {
        var resp = AuthService.getInstance().forgotPassword(
                new ForgotPasswordRequest("13800138000", "http500", "newpass", "newpass")).get(5, TimeUnit.SECONDS);
        assertEquals(500, resp.getCode());
    }

    // ============ isLoggedIn ============

    @Test
    @DisplayName("isLoggedIn - 已登录")
    void isLoggedIn_true() {
        LoginResponse login = new LoginResponse();
        login.setAccessToken("test-token");
        TokenStorage.save(login, false);
        assertTrue(AuthService.getInstance().isLoggedIn());
        TokenStorage.clear();
    }

    @Test
    @DisplayName("isLoggedIn - 未登录")
    void isLoggedIn_false() {
        TokenStorage.clear();
        assertFalse(AuthService.getInstance().isLoggedIn());
    }
}
