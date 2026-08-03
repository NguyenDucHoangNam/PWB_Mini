package com.pwb.web.dto;

import com.pwb.shared.dto.PageResponse;
import org.springframework.data.domain.Page;

import java.util.function.Function;

public final class PageResponses {

    private PageResponses() {
    }

    public static <T> PageResponse<T> from(Page<T> page) {
        return PageResponse.of(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );
    }

    public static <S, T> PageResponse<T> from(Page<S> page, Function<S, T> mapper) {
        return from(page.map(mapper));
    }
}
