package com.qrpublic.apartment.databackup.security;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component @RequiredArgsConstructor
public class BackupTokenFilter extends OncePerRequestFilter {
    private final BackupTokenStore backupTokenStore;
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        if (req.getRequestURI().equals("/api/auth/token")) { chain.doFilter(req, res); return; }
        String token = req.getHeader("X-Backup-Token");
        if (token == null || token.isBlank()) token = req.getParameter("backupToken");
        if (!backupTokenStore.isValid(token)) {
            res.setStatus(401); res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"Invalid or expired backup token\"}"); return;
        }
        chain.doFilter(req, res);
    }
}
