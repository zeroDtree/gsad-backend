package com.zerodtree.gsad.security;

import com.zerodtree.gsad.common.BusinessException;
import com.zerodtree.gsad.common.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginRateLimitServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private LoginRateLimitService loginRateLimitService;

    @BeforeEach
    void setUp() {
        loginRateLimitService = new LoginRateLimitService(redisTemplate);
    }

    @Test
    void assertAllowed_underLimit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("login:ip:127.0.0.1")).thenReturn("1");
        when(valueOperations.get("login:email:user@example.com")).thenReturn("2");

        assertThatCode(() -> loginRateLimitService.assertAllowed("127.0.0.1", "user@example.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void assertAllowed_emailOverLimit_throws() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("login:ip:127.0.0.1")).thenReturn(null);
        when(valueOperations.get("login:email:user@example.com")).thenReturn("5");

        assertThatThrownBy(() -> loginRateLimitService.assertAllowed("127.0.0.1", "user@example.com"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.RATE_LIMITED);
    }

    @Test
    void recordAttempt_incrementsKeys() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("login:ip:127.0.0.1")).thenReturn(1L);
        when(valueOperations.increment("login:email:user@example.com")).thenReturn(1L);

        loginRateLimitService.recordAttempt("127.0.0.1", "user@example.com");

        verify(valueOperations).increment("login:ip:127.0.0.1");
        verify(valueOperations).increment("login:email:user@example.com");
        verify(redisTemplate).expire(eq("login:ip:127.0.0.1"), any());
        verify(redisTemplate).expire(eq("login:email:user@example.com"), any());
    }

    @Test
    void resolveClientIp_ignoresSpoofedForwardedHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("X-Forwarded-For", "1.2.3.4");

        assertThat(LoginRateLimitService.resolveClientIp(request)).isEqualTo("10.0.0.5");
    }
}
