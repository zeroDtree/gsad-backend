package com.zerodtree.gsad.security;

import com.zerodtree.gsad.common.BusinessException;
import com.zerodtree.gsad.common.ErrorCode;
import com.zerodtree.gsad.domain.settings.api.LoginRateLimitSettingsResponse;
import com.zerodtree.gsad.domain.settings.service.LoginRateLimitSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginRateLimitServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ClientIpResolver clientIpResolver;

    @Mock
    private LoginRateLimitSettingsService settingsService;

    private LoginRateLimitService loginRateLimitService;

    @BeforeEach
    void setUp() {
        lenient().when(settingsService.get()).thenReturn(new LoginRateLimitSettingsResponse(15, 5, 30));
        loginRateLimitService = new LoginRateLimitService(redisTemplate, clientIpResolver, settingsService);
    }

    @Test
    void assertAllowed_underLimit() {
        MockHttpServletRequest request = requestWithIp("127.0.0.1");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("login:ip:127.0.0.1")).thenReturn("1");
        when(valueOperations.get("login:email:user@example.com")).thenReturn("2");

        assertThatCode(() -> loginRateLimitService.assertAllowed(request, "user@example.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void assertAllowed_emailOverLimit_throwsWithWaitMinutes() {
        MockHttpServletRequest request = requestWithIp("127.0.0.1");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("login:ip:127.0.0.1")).thenReturn(null);
        when(valueOperations.get("login:email:user@example.com")).thenReturn("5");
        when(redisTemplate.getExpire("login:email:user@example.com", TimeUnit.SECONDS)).thenReturn(700L);

        assertThatThrownBy(() -> loginRateLimitService.assertAllowed(request, "user@example.com"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException business = (BusinessException) ex;
                    assertThat(business.getErrorCode()).isEqualTo(ErrorCode.RATE_LIMITED);
                    assertThat(business.getData()).isEqualTo(new LoginRateLimitErrorData(0, 12));
                });
    }

    @Test
    void recordFailedLogin_remainingAttempts_throwsUnauthorized() {
        MockHttpServletRequest request = requestWithIp("127.0.0.1");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("login:ip:127.0.0.1")).thenReturn(1L);
        when(valueOperations.increment("login:email:user@example.com")).thenReturn(1L);
        when(valueOperations.get("login:ip:127.0.0.1")).thenReturn("1");
        when(valueOperations.get("login:email:user@example.com")).thenReturn("1");
        when(redisTemplate.getExpire("login:email:user@example.com", TimeUnit.SECONDS)).thenReturn(700L);

        assertThatThrownBy(() -> loginRateLimitService.recordFailedLogin(request, "user@example.com"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException business = (BusinessException) ex;
                    assertThat(business.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(business.getData()).isEqualTo(new LoginRateLimitErrorData(4, 12));
                });

        verify(redisTemplate).expire(eq("login:ip:127.0.0.1"), eq(Duration.ofMinutes(15)));
        verify(redisTemplate).expire(eq("login:email:user@example.com"), eq(Duration.ofMinutes(15)));
    }

    @Test
    void recordFailedLogin_missingTtl_fallsBackToWindowMinutes() {
        MockHttpServletRequest request = requestWithIp("127.0.0.1");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("login:ip:127.0.0.1")).thenReturn(1L);
        when(valueOperations.increment("login:email:user@example.com")).thenReturn(1L);
        when(valueOperations.get("login:ip:127.0.0.1")).thenReturn("1");
        when(valueOperations.get("login:email:user@example.com")).thenReturn("1");
        when(redisTemplate.getExpire("login:email:user@example.com", TimeUnit.SECONDS)).thenReturn(-1L);

        assertThatThrownBy(() -> loginRateLimitService.recordFailedLogin(request, "user@example.com"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException business = (BusinessException) ex;
                    assertThat(business.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(business.getData()).isEqualTo(new LoginRateLimitErrorData(4, 15));
                });
    }

    @Test
    void recordFailedLogin_lastAttempt_throwsRateLimited() {
        MockHttpServletRequest request = requestWithIp("127.0.0.1");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("login:ip:127.0.0.1")).thenReturn(5L);
        when(valueOperations.increment("login:email:user@example.com")).thenReturn(5L);
        when(valueOperations.get("login:ip:127.0.0.1")).thenReturn("5");
        when(valueOperations.get("login:email:user@example.com")).thenReturn("5");
        when(redisTemplate.getExpire("login:email:user@example.com", TimeUnit.SECONDS)).thenReturn(600L);

        assertThatThrownBy(() -> loginRateLimitService.recordFailedLogin(request, "user@example.com"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException business = (BusinessException) ex;
                    assertThat(business.getErrorCode()).isEqualTo(ErrorCode.RATE_LIMITED);
                    assertThat(business.getData()).isEqualTo(new LoginRateLimitErrorData(0, 10));
                });
    }

    @Test
    void clearAttempts_deletesEmailAndIpKeys() {
        MockHttpServletRequest request = requestWithIp("127.0.0.1");

        loginRateLimitService.clearAttempts(request, "User@example.com");

        verify(redisTemplate).delete("login:ip:127.0.0.1");
        verify(redisTemplate).delete("login:email:user@example.com");
    }

    private MockHttpServletRequest requestWithIp(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        when(clientIpResolver.resolve(request)).thenReturn(ip);
        return request;
    }
}
