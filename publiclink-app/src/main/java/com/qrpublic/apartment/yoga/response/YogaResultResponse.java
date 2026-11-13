package com.qrpublic.apartment.yoga.response;

import lombok.Data;

/**
 * Generic single-value envelope so controllers return a stable JSON shape for
 * booking / cancel / toggle / resend operations.
 */
@Data
public class YogaResultResponse<T> {
    private T data;

    public static <T> YogaResultResponse<T> of(T data) {
        YogaResultResponse<T> response = new YogaResultResponse<>();
        response.setData(data);
        return response;
    }
}
