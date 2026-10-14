package io.wakeharbor.control.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.List;

/**
 * Secures machine-to-machine internal worker endpoints (/api/internal/v1/**).
 * Requires the X-Worker-Token header to match WAKEHARBOR_WORKER_SECRET using constant-time comparison.
 */
@Component
public class InternalWorkerAuthFilter extends OncePerRequestFilter {

    private final String workerSecret;

    public InternalWorkerAuthFilter(@Value("${wakeharbor.worker.secret}") String workerSecret) {
        this.workerSecret = workerSecret;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        if (!path.startsWith("/api/internal/v1")) {
            filterChain.doFilter(request, response);
            return;
        }

        String providedToken = request.getHeader("X-Worker-Token");
        if (providedToken == null || !MessageDigest.isEqual(providedToken.getBytes(), workerSecret.getBytes())) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Invalid or missing internal worker secret token\"}");
            return;
        }

        // Authenticate worker request in security context
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "worker-service",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_WORKER"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        filterChain.doFilter(request, response);
    }
}
