package com.turbotax.refund.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record PageResponse<T>(
    List<T> content,
    @Schema(example = "0") int page,
    @Schema(example = "10") int size,
    @Schema(example = "104") long totalElements,
    @Schema(example = "11") int totalPages
) {
    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageResponse<>(content, page, size, totalElements, totalPages);
    }
}
