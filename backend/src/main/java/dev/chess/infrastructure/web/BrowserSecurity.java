package dev.chess.infrastructure.web;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

@Component
public class BrowserSecurity extends OncePerRequestFilter {
    private final List<String> origins;
    public BrowserSecurity(@Value("${chess.allowed-origins}") List<String> origins) { this.origins = origins; }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "same-origin");
        response.setHeader("X-Frame-Options", "DENY");
        if (request.getRequestURI().startsWith("/api/")) {
            response.setHeader("Cache-Control", "no-store");
            String origin = request.getHeader("Origin");
            if (!request.getMethod().equals("GET") && origin != null && !origins.contains(origin)) {
                response.setStatus(403); response.setContentType("application/problem+json");
                response.getWriter().write("{\"status\":403,\"title\":\"Origem não permitida\",\"detail\":\"Abra o aplicativo pelo endereço configurado.\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
