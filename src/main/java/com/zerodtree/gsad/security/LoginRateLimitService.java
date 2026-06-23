package com.zerodtree.gsad.security;

import com.zerodtree.gsad.common.BusinessException;
import com.zerodtree.gsad.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class LoginRateLimitService {

    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int MAX_ATTEMPTS_PER_EMAIL = 5;
    private static final int MAX_ATTEMPTS_PER_IP = 30;

    private static final String IP_PREFIX = "login:ip:";
    private static final String EMAIL_PREFIX = "login:email:";

    private final RedisTemplate<String, String> redisTemplate;

    public void assertAllowed(String clientIp, String email) {
        if (StringUtils.hasText(clientIp) && count(IP_PREFIX + clientIp) >= MAX_ATTEMPTS_PER_IP) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "Too many login attempts");
        }
        if (StringUtils.hasText(email) && count(EMAIL_PREFIX + normalizeEmail(email)) >= MAX_ATTEMPTS_PER_EMAIL) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "Too many login attempts");
        }
    }

    public void recordAttempt(String clientIp, String email) {
        if (StringUtils.hasText(clientIp)) {
            increment(IP_PREFIX + clientIp);
        }
        if (StringUtils.hasText(email)) {
            increment(EMAIL_PREFIX + normalizeEmail(email));
        }
    }

    private long count(String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private void increment(String key) {
        Long next = redisTemplate.opsForValue().increment(key);
        if (next != null && next == 1L) {
            redisTemplate.expire(key, WINDOW);
        }
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
