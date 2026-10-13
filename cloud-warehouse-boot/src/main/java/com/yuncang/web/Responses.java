package com.yuncang.web;

import com.yuncang.common.ApiResponse;
import com.yuncang.common.PageResult;
import com.yuncang.common.TraceContext;
import java.util.function.Function;

public final class Responses {
    private Responses() {}

    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.success(data, TraceContext.get());
    }

    public static <T, U> PageResult<U> page(PageResult<T> source, Function<T, U> mapper) {
        return new PageResult<>(source.items().stream().map(mapper).toList(),
                                source.page(), source.size(), source.total());
    }
}
