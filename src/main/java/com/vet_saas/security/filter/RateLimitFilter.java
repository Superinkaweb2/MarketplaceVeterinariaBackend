package com.vet_saas.security.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String clientIp = getClientIdentifier(request);

        String rateLimitKey = clientIp + ":" + path;

        Bucket bucket = buckets.computeIfAbsent(rateLimitKey, this::createBucket);

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.addHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
        } else {
            long waitTimeSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000;
            response.addHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(waitTimeSeconds));
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"message\":\"Demasiadas solicitudes. Intenta de nuevo en " + waitTimeSeconds + " segundos.\"}");
        }
    }

    private Bucket createBucket(String key) {
        Bandwidth limit;
        if (key.contains("/auth/login") || key.contains("/auth/register")) {
            limit = Bandwidth.simple(5, Duration.ofMinutes(1));
        } else if (key.contains("/auth/forgot-password") || key.contains("/auth/reset-password")) {
            limit = Bandwidth.simple(3, Duration.ofMinutes(5));
        } else if (key.contains("/auth/sync")) {
            limit = Bandwidth.simple(10, Duration.ofMinutes(1));
        } else if (key.contains("/payments/webhook")) {
            limit = Bandwidth.simple(100, Duration.ofMinutes(1));
        } else if (key.contains("/payments/checkout")) {
            limit = Bandwidth.simple(10, Duration.ofMinutes(1));
        } else if (key.contains("/reclamos")) {
            limit = Bandwidth.simple(5, Duration.ofMinutes(1));
        } else {
            limit = Bandwidth.simple(30, Duration.ofMinutes(1));
        }

        return Bucket.builder().addLimit(limit).build();
    }

    private String getClientIdentifier(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/auth/")
                && !path.startsWith("/api/v1/payments/webhook")
                && !path.startsWith("/api/v1/payments/checkout")
                && !path.startsWith("/api/v1/reclamos");
    }
}
