package com.qrpublic.apartment.apiGateway.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.ConnectException;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The empty-200 bug ended at this handler: failures that now propagate must be
 * answered with a real status and a browser-visible body, not swallowed.
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler(new ObjectMapper());
    }

    private MockServerWebExchange exchange(String origin) {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest
                .get("/publiclink/admin/v1/paymentPlan/catalog");
        if (origin != null) {
            request = request.header("Origin", origin);
        }
        return MockServerWebExchange.from(request.build());
    }

    @Test
    void unreachableRouteTargetBecomes503ServiceUnavailable() {
        MockServerWebExchange exchange = exchange(null);

        handler.handle(exchange, new UnknownHostException("jade.local")).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("SVC_001");
    }

    @Test
    void connectionRefusedIsAlsoTreatedAsUnreachable() {
        MockServerWebExchange exchange = exchange(null);

        handler.handle(exchange, new RuntimeException(new ConnectException("Connection refused"))).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("SVC_001");
    }

    @Test
    void unexpectedFailureBecomes500SystemErrorNotAuthError() {
        MockServerWebExchange exchange = exchange(null);

        handler.handle(exchange, new IllegalStateException("something odd")).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body).contains("SYS_001");
        assertThat(body).doesNotContain("AUTH_001");
    }

    @Test
    void errorResponseCarriesCorsHeadersForBrowserVisibility() {
        MockServerWebExchange exchange = exchange("http://localhost:3000");

        handler.handle(exchange, new UnknownHostException("jade.local")).block();

        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertThat(headers.getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo("http://localhost:3000");
        assertThat(headers.getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isEqualTo("true");
    }
}
