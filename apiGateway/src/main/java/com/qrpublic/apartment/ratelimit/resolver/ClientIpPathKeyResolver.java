package com.qrpublic.apartment.ratelimit.resolver;

import com.qrpublic.apartment.ratelimit.config.RateLimitProperties;
import com.qrpublic.apartment.ratelimit.model.CidrRange;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;

@Slf4j
@Component
public class ClientIpPathKeyResolver implements KeyResolver {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_REAL_IP = "X-Real-IP";

    private final RateLimitProperties rateLimitProperties;
    private final List<CidrRange> trustedRanges;

    public ClientIpPathKeyResolver(RateLimitProperties rateLimitProperties) {
        this.rateLimitProperties = rateLimitProperties;
        this.trustedRanges = rateLimitProperties.getTrustedProxyCidrs().stream()
                .map(CidrRange::parse)
                .toList();
    }

    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        String key = "unknown";
        try {
            ServerHttpRequest request = exchange.getRequest();
            String clientIp = resolveClientIp(request);
            String normalizedPath = normalizePath(request.getPath().value());
            key = rateLimitProperties.getRedisKeyPrefix() + clientIp + ":" + normalizedPath;
        } catch (Exception e) {
            log.warn("Failed to resolve client IP for rate limiting", e);
        }
        return Mono.just(key);
    }

    private String normalizePath(String path) {
        if (Strings.isBlank(path)) return "/";
        String normalized = path.toLowerCase();
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String resolveClientIp(ServerHttpRequest request) {
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        Assert.notNull(remoteAddress, "Remote address is null");
        String directIp = normalizeIp(remoteAddress.getAddress().getHostAddress());
        // only trust forwarding header if direct connection is from a trusted proxy
        if (isTrustedProxy(directIp)) {
            String xff = request.getHeaders().getFirst(X_FORWARDED_FOR);
            if (xff != null && !xff.isBlank()) {
                String[] ips = xff.split(",");
                for (String ip : ips) {
                    ip = normalizeIp(ip.trim());
                    if (!ip.isBlank()) {
                        return ip;
                    }
                }
            }
            String realIp = request.getHeaders().getFirst(X_REAL_IP);
            if (realIp != null && !realIp.isBlank()) {
                return normalizeIp(realIp.trim());
            }
        } else if (request.getHeaders().containsKey(X_FORWARDED_FOR)) {
            log.warn("Ignoring X-Forwarded-For header from untrusted proxy {}: {}", directIp, request.getHeaders().get(X_FORWARDED_FOR));
        }
        return directIp;
    }

    private boolean isTrustedProxy(String directIp) {
        if (trustedRanges.isEmpty()) {
            return false; // no trusted proxies configured, do not trust any
        }
        try {
            InetAddress address = InetAddress.getByName(directIp);
            return trustedRanges.stream().anyMatch(range -> range.contains(address));
        } catch (UnknownHostException e) {
            log.warn("Failed to parse direct client IP: {}", directIp, e);
            return false;
        }
    }

    private String normalizeIp(String ip) {
        Assert.notNull(ip, "IP address is null");
        // strip IPv6
        int zoneIdx = ip.indexOf('%');
        if (zoneIdx > 0) {
            ip = ip.substring(0, zoneIdx);
        }
        // convert Ipv6-mapped IPv4 (::ffff:10.0.0.1) to plain IPv4
        if (ip.startsWith("::ffff:") || ip.startsWith("0:0:0:0:0:ffff:")) {
            String v4Part = ip.substring(ip.lastIndexOf(':') + 1);
            if (v4Part.contains(".")) {
                return v4Part;
            }
        }
        //strip brackets for IPv6
        if (ip.startsWith("[") && ip.endsWith("]")) {
            ip = ip.substring(1, ip.length() - 1);
        }
        return ip;
    }
}
