package org.example.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工具类测试示例
 * 演示 JUnit 5 的各种测试特性
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@DisplayName("工具类测试")
class StringUtilsTest {

    @Nested
    @DisplayName("字符串非空检查")
    class IsNotBlankTests {

        @Test
        @DisplayName("非空字符串返回 true")
        void shouldReturnTrueForNonBlankString() {
            assertTrue(StringUtils.isNotBlank("hello"));
        }

        @Test
        @DisplayName("空字符串返回 false")
        void shouldReturnFalseForEmptyString() {
            assertFalse(StringUtils.isNotBlank(""));
        }

        @Test
        @DisplayName("null 返回 false")
        void shouldReturnFalseForNull() {
            assertFalse(StringUtils.isNotBlank(null));
        }

        @ParameterizedTest
        @DisplayName("各种空白字符返回 false")
        @ValueSource(strings = {" ", "\t", "\n", "\r", "  "})
        void shouldReturnFalseForWhitespace(String input) {
            assertFalse(StringUtils.isNotBlank(input));
        }
    }

    @Nested
    @DisplayName("字符串截取")
    class SubstringTests {

        @Test
        @DisplayName("正常截取")
        void shouldSubstringCorrectly() {
            String result = StringUtils.substring("hello", 1, 3);
            assertEquals("el", result);
        }

        @Test
        @DisplayName("截取超出长度")
        void shouldHandleSubstringBeyondLength() {
            String result = StringUtils.substring("hi", 0, 10);
            assertEquals("hi", result);
        }

        @ParameterizedTest
        @DisplayName("参数化截取测试")
        @CsvSource({
                "hello, 0, 5, hello",
                "hello, 1, 4, ell",
        })
        void shouldSubstringWithVariousInputs(String input, int start, int end, String expected) {
            String result = StringUtils.substring(input, start, end);
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("截取空结果返回空字符串")
        void shouldReturnEmptyForZeroLengthSubstring() {
            String result = StringUtils.substring("hello", 2, 2);
            assertEquals("", result);
        }
    }

    @Nested
    @DisplayName("空值安全操作")
    class NullSafetyTests {

        @ParameterizedTest
        @DisplayName("null 和空值处理")
        @NullAndEmptySource
        @ValueSource(strings = {" ", "  "})
        void shouldHandleNullAndEmpty(String input) {
            String result = StringUtils.defaultIfBlank(input, "default");
            assertEquals("default", result);
        }

        @Test
        @DisplayName("非空值不使用默认值")
        void shouldNotUseDefaultForNonBlank() {
            String result = StringUtils.defaultIfBlank("actual", "default");
            assertEquals("actual", result);
        }
    }

    /**
     * 模拟的 StringUtils 类
     * 实际项目中应使用 Apache Commons Lang 或类似库
     */
    static class StringUtils {

        static boolean isNotBlank(String str) {
            return str != null && !str.trim().isEmpty();
        }

        static String substring(String str, int start, int end) {
            if (str == null) {
                return null;
            }
            if (start < 0) {
                start = 0;
            }
            if (end > str.length()) {
                end = str.length();
            }
            if (start >= end) {
                return "";
            }
            return str.substring(start, end);
        }

        static String defaultIfBlank(String str, String defaultStr) {
            return isNotBlank(str) ? str : defaultStr;
        }
    }
}
