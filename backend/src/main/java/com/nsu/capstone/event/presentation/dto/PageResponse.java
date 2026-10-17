package com.nsu.capstone.event.presentation.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** offset 방식 페이지 응답(00 공통 규칙 §5). */
public record PageResponse<T>(
    List<T> items,
    int page,
    int size,
    long totalElements,
    int totalPages
) {

    public static <S, T> PageResponse<T> from(Page<S> page, Function<S, T> mapper) {
        return new PageResponse<>(
            page.getContent().stream().map(mapper).toList(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }
}
