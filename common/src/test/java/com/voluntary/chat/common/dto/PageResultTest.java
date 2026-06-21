package com.voluntary.chat.common.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PageResult 测试")
class PageResultTest {

    @Test
    @DisplayName("无参构造默认值")
    void defaultValues() {
        PageResult<String> result = new PageResult<>();
        assertNotNull(result.getList());
        assertTrue(result.getList().isEmpty());
        assertEquals(0, result.getTotal());
        assertEquals(0, result.getPage());
        assertEquals(0, result.getSize());
    }

    @Test
    @DisplayName("全参构造")
    void allArgsConstructor() {
        List<String> items = List.of("a", "b");
        PageResult<String> result = new PageResult<>(items, 10, 1, 20);
        assertEquals(2, result.getList().size());
        assertEquals(10, result.getTotal());
        assertEquals(1, result.getPage());
        assertEquals(20, result.getSize());
    }

    @Test
    @DisplayName("Builder 构造")
    void builder() {
        PageResult<String> result = PageResult.<String>builder()
                .list(List.of("a"))
                .total(1)
                .page(1)
                .size(20)
                .build();
        assertEquals(1, result.getList().size());
        assertEquals(20, result.getSize());
    }

    @Test
    @DisplayName("of 工厂方法")
    void ofFactoryMethod() {
        List<String> items = List.of("x", "y", "z");
        PageResult<String> result = PageResult.of(items, 3, 1, 10);

        assertEquals(3, result.getList().size());
        assertEquals(3, result.getTotal());
        assertEquals(1, result.getPage());
        assertEquals(10, result.getSize());
    }

    @Test
    @DisplayName("of 工厂方法应创建列表副本")
    void ofFactoryMethodShouldCopyList() {
        List<String> original = new java.util.ArrayList<>(List.of("a"));
        PageResult<String> result = PageResult.of(original, 1, 1, 10);
        original.add("b"); // 修改原始列表
        assertEquals(1, result.getList().size()); // 不应受影响
    }

    @Test
    @DisplayName("Builder 默认空列表")
    void builderDefaultEmptyList() {
        PageResult<String> result = PageResult.<String>builder().build();
        assertNotNull(result.getList());
        assertTrue(result.getList().isEmpty());
    }

    @Test
    @DisplayName("Setter 和 Getter")
    void setterAndGetter() {
        PageResult<String> result = new PageResult<>();
        result.setList(List.of("test"));
        result.setTotal(100);
        result.setPage(2);
        result.setSize(50);

        assertEquals(1, result.getList().size());
        assertEquals(100, result.getTotal());
        assertEquals(2, result.getPage());
        assertEquals(50, result.getSize());
    }
}
