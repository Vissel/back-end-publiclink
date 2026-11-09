package com.qrpublic.apartment.chat.template;

import com.qrpublic.apartment.chat.template.model.Result;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

/**
 * Turns a service {@link Result} into the reactive controller response, carrying
 * the failure code through as the HTTP status.
 */
public class ResponseEntityConvertor {

    public static <T> Mono<ResponseEntity<T>> convertToMonoResponseEntity(Result<T> result) {
        return Mono.just(result)
                .map(r -> {
                    if (r.isSuccess()) {
                        return ResponseEntity.ok(r.getData());
                    } else {
                        return ResponseEntity.status(r.getErrorCode()).body(r.getData());
                    }
                });
    }
}
