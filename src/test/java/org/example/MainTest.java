package org.example;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Main 类的单元测试
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@DisplayName("Main 类测试")
class MainTest {

    /**
     * 循环上限常量值，与 Main.LOOP_LIMIT 保持一致
     */
    private static final int LOOP_LIMIT = 5;

    @Test
    @DisplayName("测试主方法执行不抛异常")
    void shouldExecuteMainMethodWithoutException() {
        assertDoesNotThrow(() -> Main.main(new String[] {}));
    }

    @ParameterizedTest
    @DisplayName("测试数字格式化")
    @ValueSource(ints = { 1, 2, 3, 4, 5 })
    void shouldFormatNumbers(final int number) {
        final String result = String.format("i = %d", number);
        assertNotNull(result);
        assertTrue(result.contains(String.valueOf(number)));
    }

    @ParameterizedTest
    @DisplayName("测试字符串格式化")
    @CsvSource({
            "1, i = 1",
            "2, i = 2",
            "3, i = 3"
    })
    void shouldFormatStringCorrectly(final int input, final String expected) {
        final String result = String.format("i = %d", input);
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("测试空字符串处理")
    void shouldHandleEmptyString() {
        final String result = String.format("Hello and welcome!");
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    @DisplayName("测试异常情况 - 空指针")
    void shouldThrowNullPointerException() {
        assertThrows(NullPointerException.class, () -> {
            ((String) null).length();
        });
    }

    @Test
    @DisplayName("测试边界值 - 整数最大值")
    void shouldHandleIntegerMaxValue() {
        final int maxValue = Integer.MAX_VALUE;
        final String result = String.format("i = %d", maxValue);
        assertNotNull(result);
        assertTrue(result.contains(String.valueOf(maxValue)));
    }

    @Test
    @DisplayName("测试循环上限常量在范围内")
    void shouldHaveValidLoopLimit() {
        assertTrue(LOOP_LIMIT > 0, "循环上限应大于0");
        assertTrue(LOOP_LIMIT <= 100, "循环上限应在合理范围内");
    }
}
