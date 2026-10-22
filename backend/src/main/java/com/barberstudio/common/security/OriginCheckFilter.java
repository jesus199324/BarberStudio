package com.barberstudio.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Defensa CSRF para la API privada (el JWT viaja en cookie): las peticiones que modifican datos
 * y declaran un Origin no permitido se rechazan. Se combina con cookie SameSite.
 */
public class OriginCheckFilter extends OncePerRequestFilter {
    private static final Set<String> SAFE = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private final Set<String> allowed;

    public OriginCheckFilter(Set<String> allowed) { this.allowed = allowed; }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String path = req.getRequestURI();
        boolean guarded = !SAFE.contains(req.getMethod()) && path.startsWith("/api/") && !path.startsWith("/api/public/");
        String origin = req.getHeader("Origin");
        if (guarded && origin != null && !allowed.contains(origin)) {
            res.sendError(HttpServletResponse.SC_FORBIDDEN, "Origen no permitido");
            return;
        }
        chain.doFilter(req, res);
    }
}
