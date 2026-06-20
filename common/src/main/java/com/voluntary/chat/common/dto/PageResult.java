package com.voluntary.chat.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    @Builder.Default
    private List<T> list = Collections.emptyList();
    private long total;
    private int page;
    private int size;

    public static <T> PageResult<T> of(List<T> list, long total, int page, int size) {
        return PageResult.<T>builder()
                .list(new ArrayList<>(list))
                .total(total)
                .page(page)
                .size(size)
                .build();
    }
}
