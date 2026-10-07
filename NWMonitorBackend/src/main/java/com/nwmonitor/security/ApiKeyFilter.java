package com.nwmonitor.security;

import com.nwmonitor.ingest.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** Authenticates collector agents on /api/v1/ingest/** via the X-API-Key header. */
public class ApiKeyFilter extends OncePerRequestFilter {
    private final ApiKeyService keys;

    public ApiKeyFilter(ApiKeyService keys) { this.keys = keys; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/ingest");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (keys.matches(req.getHeader("X-API-Key"))) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    "collector", null, List.of(new SimpleGrantedAuthority("ROLE_COLLECTOR"))));
        }
        chain.doFilter(req, res);
    }
}
