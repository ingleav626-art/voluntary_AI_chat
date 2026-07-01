package com.voluntary.chat.server.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MyMetaObjectHandler 测试
 *
 * <p>
 * MyBatis-Plus 的 strictInsertFill/strictUpdateFill 需要完整的 MyBatis 运行时环境
 * （TableInfo、Entity 注解等），单元测试中无法模拟。
 * 这里的自动填充逻辑在集成测试中覆盖。
 * </p>
 */
@DisplayName("MyMetaObjectHandler 测试")
class MyMetaObjectHandlerTest {

    private MyMetaObjectHandler handler;

    @BeforeEach
    void setUp() {
        handler = new MyMetaObjectHandler();
    }

    @Disabled("需要完整的 MyBatis-Plus 运行时环境才能测试")
    @Test
    @DisplayName("insertFill 填充 createTime 和 updateTime")
    void insertFill_shouldSetCreateAndUpdateTime() {
        // strictInsertFill/strictUpdateFill 需要运行时 TableInfo，
        // 不能在纯单元测试中验证
    }

    @Disabled("需要完整的 MyBatis-Plus 运行时环境才能测试")
    @Test
    @DisplayName("updateFill 填充 updateTime")
    void updateFill_shouldSetUpdateTime() {
    }
}
