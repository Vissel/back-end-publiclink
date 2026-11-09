package com.qrpublic.apartment.apiGateway.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Configuration
@Order(-2)
@Slf4j
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        log.error("Global exception handler caught: ", ex);

        ErrorCode errorCode;
        HttpStatus status;

        if (ex instanceof BusinessException businessEx) {
            errorCode = businessEx.getErrorCode();
            status = errorCode.getHttpStatus();
        } else {
            errorCode = ErrorCode.AUTHENTICATION_FAILED;
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        Map<String, Object> errorResponse = Map.of(
                "code", errorCode.getCode(),
                "message", errorCode.getMessage(),
                "timestamp", System.currentTimeMillis(),
                "path", exchange.getRequest().getURI().getPath()
        );

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        DataBuffer buffer = exchange.getResponse()
                .bufferFactory()
                .wrap(toJson(errorResponse).getBytes(StandardCharsets.UTF_8));

        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private String toJson(Map<String, Object> map) {
        // Use Jackson in production
        return "{" +
                "\"code\":\"" + map.get("code") + "\"," +
                "\"message\":\"" + map.get("message") + "\"," +
                "\"timestamp\":" + map.get("timestamp") + "," +
                "\"path\":\"" + map.get("path") + "\"" +
                "}";
    }
}