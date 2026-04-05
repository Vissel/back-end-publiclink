package com.qrpublic.apartment.gatewayConfig;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ") && !authHeader.substring(7).contains("undefined")) {
            String token = authHeader.substring(7);
            String username = "";
//                    jwtService.extractSubject(token);

//            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
//                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
//                Collection<GrantedAuthority> authorities = userDetails.getAuthorities().stream()
//                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getAuthority()))
//                        .collect(Collectors.toSet());
////                if (jwtService.isTokenValid(token)) {
//                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(userDetails,
//                        null, authorities);
//
//                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
//
//                SecurityContextHolder.getContext().setAuthentication(authToken);
////                }
//            }
        }

        filterChain.doFilter(request, response);
    }
}
