package com.example.mockinterview.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitingFilter.class);
    private final StringRedisTemplate redisTemplate;

    public RateLimitingFilter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        // Only rate limit API paths
        if (!path.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        if (redisTemplate == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.trim().isEmpty()) {
            ip = request.getRemoteAddr();
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String limitKey;
        int maxRequests;
        int windowSeconds = 60;

        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            limitKey = "ratelimit:uid:" + auth.getName();
            maxRequests = 100; // Limit for logged-in users
        } else {
            limitKey = "ratelimit:ip:" + ip;
            maxRequests = 20; // Limit for guest IP
        }

        try {
            Long count = redisTemplate.opsForValue().increment(limitKey);
            if (count != null && count == 1) {
                redisTemplate.expire(limitKey, Duration.ofSeconds(windowSeconds));
            }

            if (count != null && count > maxRequests) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Too many requests. Please try again later.\"}");
                return;
            }
        } catch (Exception e) {
            logger.warn("Redis rate limiter unavailable: " + e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
