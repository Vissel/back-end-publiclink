package com.qrpublic.apartment.template;

import com.qrpublic.apartment.template.model.Result;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

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
