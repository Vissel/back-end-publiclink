package com.qrpublic.apartment.apiGateway.filter;

import com.qrpublic.apartment.apiGateway.authentication.JwtTokenProducer;
import com.qrpublic.apartment.apiGateway.constant.FilterConstant;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.mock.http.server.reactive.MockServerHttpRequest.get;

/**
 * Regression tests for the empty-200 catalog bug: a downstream failure used to
 * be swallowed by the trailing onErrorResume around chain.filter, which
 * re-entered
 * the already-routed exchange and answered 200 with no body. The filter must
 * now
 * run the chain exactly once and let downstream errors propagate untouched.
 */
class JwtAuthenticationWebFilterTest {

    private JwtTokenProducer jwtTokenProducer;
    private JwtAuthenticationWebFilter filter;
    private WebFilterChain chain;

    @BeforeEach
    void setUp() {
        jwtTokenProducer = mock(JwtTokenProducer.class);
        filter = new JwtAuthenticationWebFilter();
        ReflectionTestUtils.setField(filter, "jwtTokenProducer", jwtTokenProducer);
        chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    private void stubValidAccessToken(String token, String username, List<String> roles) {
        Claims claims = mock(Claims.class);
        when(jwtTokenProducer.isTokenValid(token)).thenReturn(true);
        when(jwtTokenProducer.getUsernameFromToken(token)).thenReturn(Mono.just(username));
        when(jwtTokenProducer.extractClaims(token)).thenReturn(claims);
        when(claims.get(FilterConstant.TOKEN_TYPE_CLAIM, String.class)).thenReturn(FilterConstant.ACCESS_TOKEN_TYPE);
        when(claims.get(FilterConstant.ROLES_CLAIM)).thenReturn(roles);
    }

    @Test
    void downstreamErrorPropagatesAndChainIsInvokedExactlyOnce() {
        // The bug: the old onErrorResume caught this error and called
        // chain.filter a second time on the already-routed exchange, producing
        // an empty HTTP 200 instead of a real failure.
        when(chain.filter(any())).thenReturn(Mono.error(new IllegalStateException("downstream is down")));
        MockServerWebExchange exchange = MockServerWebExchange.from(get("/publiclink/admin/v1/paymentPlan/catalog")
                .header("Authorization", "Bearer good-token"));
        when(jwtTokenProducer.isTokenValid("good-token")).thenReturn(true);
        when(jwtTokenProducer.getUsernameFromToken("good-token"))
                .thenReturn(Mono.error(new RuntimeException("boom")));

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyErrorMessage("downstream is down");

        verify(chain, times(1)).filter(any());
    }

    @Test
    void downstreamErrorOnAnonymousRequestIsNotSwallowed() {
        when(chain.filter(any())).thenReturn(Mono.error(new IllegalStateException("no route")));
        MockServerWebExchange exchange = MockServerWebExchange.from(get("/publiclink/api/v1/publish/plans"));

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyErrorMessage("no route");

        verify(chain, times(1)).filter(any());
    }

    @Test
    void validAccessTokenForwardsIdentityOnceWithSecurityContext() {
        stubValidAccessToken("good-token", "admin", List.of("Admin"));
        MockServerWebExchange exchange = MockServerWebExchange.from(get("/publiclink/admin/v1/paymentPlan/catalog")
                .header("Authorization", "Bearer good-token"));

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        AtomicReference<SecurityContext> securityContextSeen = new AtomicReference<>();
        // Read the security context the way downstream code does: through the
        // subscriber context written by ReactiveSecurityContextHolder.
        when(chain.filter(any())).thenReturn(ReactiveSecurityContextHolder.getContext()
                .doOnNext(securityContextSeen::set)
                .onErrorResume(e -> Mono.empty())
                .then());
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(securityContextSeen.get()).isNotNull();
        assertThat(securityContextSeen.get().getAuthentication().getName()).isEqualTo("admin");
        verify(chain, times(1)).filter(captor.capture());
        ServerWebExchange forwarded = captor.getValue();
        assertThat(forwarded.getRequest().getHeaders().getFirst(FilterConstant.USER_ID_HEADER)).isEqualTo("admin");
        assertThat(forwarded.getRequest().getHeaders().containsKey(FilterConstant.TRACE_ID_HEADER)).isTrue();
        assertThat(forwarded.getRequest().getHeaders().containsKey(FilterConstant.AUTH_TIMESTAMP_HEADER)).isTrue();
    }

    @Test
    void spoofedIdentityHeadersAreStrippedForAnonymousRequests() {
        MockServerWebExchange exchange = MockServerWebExchange.from(get("/publiclink/api/v1/publish/plans")
                .header(FilterConstant.USER_ID_HEADER, "hacker"));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain, times(1)).filter(captor.capture());
        assertThat(captor.getValue().getRequest().getHeaders().getFirst(FilterConstant.USER_ID_HEADER)).isNull();
    }

    @Test
    void invalidTokenContinuesAnonymously() {
        when(jwtTokenProducer.isTokenValid(anyString())).thenReturn(false);
        MockServerWebExchange exchange = MockServerWebExchange.from(get("/publiclink/admin/v1/paymentPlan/catalog")
                .header("Authorization", "Bearer expired-token"));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain, times(1)).filter(captor.capture());
        assertThat(captor.getValue().getRequest().getHeaders().getFirst(FilterConstant.USER_ID_HEADER)).isNull();
    }

    @Test
    void refreshTokenTypeIsRejectedWith401AndNeverReachesTheChain() {
        Claims claims = mock(Claims.class);
        when(jwtTokenProducer.isTokenValid("refresh-token")).thenReturn(true);
        when(jwtTokenProducer.getUsernameFromToken("refresh-token")).thenReturn(Mono.just("admin"));
        when(jwtTokenProducer.extractClaims("refresh-token")).thenReturn(claims);
        when(claims.get(eq(FilterConstant.TOKEN_TYPE_CLAIM), eq(String.class)))
                .thenReturn(FilterConstant.REFRESH_TOKEN_TYPE);
        MockServerWebExchange exchange = MockServerWebExchange.from(get("/publiclink/admin/v1/paymentPlan/catalog")
                .header("Authorization", "Bearer refresh-token"));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void tokenWithoutRolesContinuesAnonymously() {
        stubValidAccessToken("no-role-token", "admin", List.of());
        MockServerWebExchange exchange = MockServerWebExchange.from(get("/publiclink/admin/v1/paymentPlan/catalog")
                .header("Authorization", "Bearer no-role-token"));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain, times(1)).filter(captor.capture());
        assertThat(captor.getValue().getRequest().getHeaders().getFirst(FilterConstant.USER_ID_HEADER)).isNull();
    }
}
