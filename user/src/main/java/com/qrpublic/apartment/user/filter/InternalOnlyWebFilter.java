package com.qrpublic.apartment.user.filter;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Optional;

/**
 * Restricts the internal admin registration endpoints to the server host.
 *
 * Behind nginx every hop looks like localhost, so the socket remote address is
 * not enough: the true client address arrives in X-Real-IP, which nginx
 * overwrites from $remote_addr. Allow only when BOTH the immediate hop is
 * loopback AND X-Real-IP is absent (direct local call) or itself loopback.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InternalOnlyWebFilter implements WebFilter {

    private static final String INTERNAL_PREFIX = "/api/v1/user/admin/internal";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!path.contains(INTERNAL_PREFIX)) {
            return chain.filter(exchange);
        }

        boolean fromLoopback = Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                .map(InetSocketAddress::getAddress)
                .map(InetAddress::isLoopbackAddress)
                .orElse(false);

        String realIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        boolean realIpLoopback = realIp == null || isLoopback(realIp);

        if (!fromLoopback || !realIpLoopback) {
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }

    private boolean isLoopback(String ip) {
        try {
            return InetAddress.getByName(ip).isLoopbackAddress();
        } catch (Exception e) {
            return false;
        }
    }
}
