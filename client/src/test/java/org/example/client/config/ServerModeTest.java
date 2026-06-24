package org.example.client.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ServerMode枚举测试
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
class ServerModeTest {

    @Test
    void testFromCode_Local() {
        final ServerMode mode = ServerMode.fromCode("local");
        assertEquals(ServerMode.LOCAL, mode);
        assertEquals("本地模式", mode.getDescription());
        assertEquals("http://localhost:8080/api", mode.getDefaultBaseUrl());
    }

    @Test
    void testFromCode_Hotspot() {
        final ServerMode mode = ServerMode.fromCode("hotspot");
        assertEquals(ServerMode.HOTSPOT, mode);
        assertEquals("热点模式", mode.getDescription());
        assertNull(mode.getDefaultBaseUrl());
    }

    @Test
    void testFromCode_Cloud() {
        final ServerMode mode = ServerMode.fromCode("cloud");
        assertEquals(ServerMode.CLOUD, mode);
        assertEquals("云端模式", mode.getDescription());
        assertNull(mode.getDefaultBaseUrl());
    }

    @Test
    void testFromCode_Invalid() {
        final ServerMode mode = ServerMode.fromCode("invalid");
        assertEquals(ServerMode.LOCAL, mode); // 默认返回LOCAL
    }

    @Test
    void testFromCode_Null() {
        final ServerMode mode = ServerMode.fromCode(null);
        assertEquals(ServerMode.LOCAL, mode); // 默认返回LOCAL
    }

    @Test
    void testGetCode() {
        assertEquals("local", ServerMode.LOCAL.getCode());
        assertEquals("hotspot", ServerMode.HOTSPOT.getCode());
        assertEquals("cloud", ServerMode.CLOUD.getCode());
    }
}