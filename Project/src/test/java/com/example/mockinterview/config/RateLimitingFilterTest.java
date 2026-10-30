package com.example.mockinterview.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RateLimitingFilterTest {

    private StringRedisTemplate redis;
    private RateLimitingFilter filter;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        filter = new RateLimitingFilter(redis);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void stubCount(long count) {
        when(redis.execute(any(RedisScript.class), any(List.class), any())).thenReturn(count);
    }

    private MockHttpServletResponse run(MockHttpServletRequest request, MockFilterChain chain) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void guestUnderLimitPassesThroughAndIsKeyedByRemoteIp() throws Exception {
        stubCount(20);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/newsletter");
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("X-Forwarded-For", "1.2.3.4"); // spoofed header must be ignored
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = run(request, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
        verify(redis).execute(any(RedisScript.class), eq(List.of("ratelimit:ip:10.0.0.5")), eq("60"));
    }

    @Test
    void guestOverLimitGets429AndChainStops() throws Exception {
        stubCount(21);
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = run(new MockHttpServletRequest("POST", "/api/newsletter"), chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void authenticatedUserIsKeyedByIdentityWithHigherLimit() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice@example.com", null, List.of()));
        stubCount(100);
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = run(new MockHttpServletRequest("GET", "/api/interviews/list"), chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(redis).execute(any(RedisScript.class), eq(List.of("ratelimit:uid:alice@example.com")), eq("60"));
    }

    @Test
    void redisOutageFailsOpen() throws Exception {
        when(redis.execute(any(RedisScript.class), any(List.class), any()))
                .thenThrow(new RedisConnectionFailureException("down"));
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = run(new MockHttpServletRequest("GET", "/api/interviews/list"), chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void nonApiPathsAreNotRateLimited() throws Exception {
        run(new MockHttpServletRequest("GET", "/dashboard"), new MockFilterChain());

        verifyNoInteractions(redis);
    }
}
