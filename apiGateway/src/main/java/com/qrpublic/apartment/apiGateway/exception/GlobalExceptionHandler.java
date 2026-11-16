package com.qrpublic.apartment.apiGateway.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Last resort for anything that escaped a filter or a route.
 *
 * <p>
 * The classification matters more than the body: this handler used to label
 * every non-business failure "Invalid username or password" (AUTH_001) with a
 * 500, so an unreachable publiclink-app - a wrong PUBLICLINK_SERVICE_URL, a
 * stopped app, a Mac hostname Windows cannot resolve - looked exactly like a
 * credential problem and sent people debugging login instead of routing.
 * Downstream reachability is now reported as 503 SVC_001, downstream HTTP
 * statuses are passed through, and only a genuine surprise stays a 500.
 */
@Configuration
@Order(-2)
@Slf4j
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    public GlobalExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        log.error("Global exception handler caught: ", ex);

        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }

        String code;
        String message;
        HttpStatus status;

        if (ex instanceof BusinessException businessEx) {
            ErrorCode errorCode = businessEx.getErrorCode();
            code = errorCode.getCode();
            message = errorCode.getMessage();
            status = errorCode.getHttpStatus();
        } else if (ex instanceof ResponseStatusException statusEx) {
            // The gateway's own routing verdicts (no route, service unavailable).
            status = HttpStatus.valueOf(statusEx.getStatusCode().value());
            code = "HTTP_" + status.value();
            message = statusEx.getReason() != null ? statusEx.getReason() : status.getReasonPhrase();
        } else if (ex instanceof WebClientResponseException responseEx) {
            // A downstream answer the gateway called itself: keep its status so
            // the caller can still tell 400 from 404 from 409.
            status = HttpStatus.valueOf(responseEx.getStatusCode().value());
            code = "HTTP_" + status.value();
            message = responseEx.getStatusText() != null ? responseEx.getStatusText() : status.getReasonPhrase();
        } else if (isUnreachable(ex)) {
            code = ErrorCode.SERVICE_UNAVAILABLE.getCode();
            message = ErrorCode.SERVICE_UNAVAILABLE.getMessage();
            status = ErrorCode.SERVICE_UNAVAILABLE.getHttpStatus();
            // One greppable line naming the target that did not answer, so the
            // operator does not have to read a stack trace to tell a DNS failure
            // from a refused connection.
            log.error("Downstream unreachable for [{}]: {}", exchange.getRequest().getURI().getPath(),
                    rootCauseMessage(ex));
        } else {
            code = ErrorCode.INTERNAL_ERROR.getCode();
            message = ErrorCode.INTERNAL_ERROR.getMessage();
            status = ErrorCode.INTERNAL_ERROR.getHttpStatus();
        }

        Map<String, Object> errorResponse = new LinkedHashMap<>();
        errorResponse.put("code", code);
        errorResponse.put("message", message);
        errorResponse.put("timestamp", System.currentTimeMillis());
        errorResponse.put("path", exchange.getRequest().getURI().getPath());

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        applyCors(exchange);

        byte[] body = toJson(errorResponse).getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    /**
     * A failure that escaped before Spring Security's CORS filter still has to
     * carry CORS headers, otherwise the browser hides the body and axios reports
     * a network error - which reads as "server down" even for a plain 400.
     * Mirrors the configured policy: any origin pattern, credentials allowed.
     */
    private void applyCors(ServerWebExchange exchange) {
        String origin = exchange.getRequest().getHeaders().getOrigin();
        if (origin == null || origin.isBlank()) {
            return;
        }
        HttpHeaders headers = exchange.getResponse().getHeaders();
        headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
        headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        headers.add(HttpHeaders.VARY, HttpHeaders.ORIGIN);
    }

    /**
     * DNS, refused connections and timeouts anywhere in the cause chain: the
     * route target is not answering, which is an outage, not a credential error.
     */
    private boolean isUnreachable(Throwable ex) {
        for (Throwable current = ex; current != null; current = current.getCause()) {
            if (current instanceof UnknownHostException
                    || current instanceof ConnectException
                    || current instanceof NoRouteToHostException
                    || current instanceof SocketTimeoutException
                    || current instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return false;
    }

    /**
     * Deepest cause text: "jade.local" for DNS, "host:port" for a refused connect.
     */
    private String rootCauseMessage(Throwable ex) {
        Throwable deepest = ex;
        while (deepest.getCause() != null && deepest.getCause() != deepest) {
            deepest = deepest.getCause();
        }
        String message = deepest.getMessage();
        return deepest.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            // Never let serialisation hide the original failure.
            log.error("Could not serialise the error response", e);
            return "{\"code\":\"" + map.get("code") + "\",\"message\":\"" + map.get("message") + "\"}";
        }
    }
}
