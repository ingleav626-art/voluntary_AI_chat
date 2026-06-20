package org.example.client.util;

import java.io.IOException;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JSON 序列化工具
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
public final class JsonUtils {

    private static final Logger LOG = LoggerFactory.getLogger(JsonUtils.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private JsonUtils() {
        // 工具类禁止实例化
    }

    /**
     * 对象转 JSON 字符串
     *
     * @param obj 对象
     * @return JSON 字符串
     */
    public static String toJson(final Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (final IOException e) {
            LOG.error("序列化失败: {}", obj, e);
            return null;
        }
    }

    /**
     * JSON 字符串转对象
     *
     * @param json JSON 字符串
     * @param clazz 目标类型
     * @param <T> 类型参数
     * @return 对象
     */
    public static <T> T fromJson(final String json, final Class<T> clazz) {
        if (json == null || json.isEmpty()) {
            LOG.warn("JSON 字符串为空");
            return null;
        }
        try {
            return MAPPER.readValue(json, clazz);
        } catch (final IOException e) {
            LOG.error("反序列化失败: {}", json, e);
            return null;
        }
    }

    /**
     * JSON 字符串转泛型对象
     *
     * @param json JSON 字符串
     * @param javaType Java 类型
     * @param <T> 类型参数
     * @return 对象
     */
    public static <T> T fromJson(final String json, final JavaType javaType) {
        if (json == null || json.isEmpty()) {
            LOG.warn("JSON 字符串为空");
            return null;
        }
        try {
            return MAPPER.readValue(json, javaType);
        } catch (final IOException e) {
            LOG.error("反序列化失败: {}", json, e);
            return null;
        }
    }

    /**
     * 获取 ObjectMapper 实例
     *
     * @return ObjectMapper
     */
    public static ObjectMapper getMapper() {
        return MAPPER;
    }
}

