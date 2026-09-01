package com.zerodtree.gsad.security;

import com.zerodtree.gsad.common.BusinessException;
import com.zerodtree.gsad.common.ErrorCode;
import com.zerodtree.gsad.domain.settings.api.LoginRateLimitSettingsResponse;
import com.zerodtree.gsad.domain.settings.service.LoginRateLimitSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class LoginRateLimitService {

    private static final String IP_PREFIX = "login:ip:";
    private static final String EMAIL_PREFIX = "login:email:";

    private final RedisTemplate<String, String> redisTemplate;
    private final ClientIpResolver clientIpResolver;
    private final LoginRateLimitSettingsService settingsService;

    public void assertAllowed(HttpServletRequest request, String email) {
        LoginRateLimitSettingsResponse settings = settingsService.get();
        String clientIp = clientIpResolver.resolve(request);
        String ipKey = ipKey(clientIp);
        String emailKey = emailKey(email);

        boolean ipBlocked = ipKey != null && count(ipKey) >= settings.maxAttemptsPerIp();
        boolean emailBlocked = emailKey != null && count(emailKey) >= settings.maxAttemptsPerEmail();
        if (ipBlocked || emailBlocked) {
            throw rateLimited(ipKey, emailKey, ipBlocked, emailBlocked, settings.windowMinutes());
        }
    }

    /**
     * Records a failed login and always throws: {@code RATE_LIMITED} when no attempts remain,
     * otherwise {@code UNAUTHORIZED} with remaining attempts.
     */
    public void recordFailedLogin(HttpServletRequest request, String email) {
        LoginRateLimitSettingsResponse settings = settingsService.get();
        Duration window = Duration.ofMinutes(settings.windowMinutes());
        String clientIp = clientIpResolver.resolve(request);
        String ipKey = ipKey(clientIp);
        String emailKey = emailKey(email);

        if (ipKey != null) {
            increment(ipKey, window);
        }
        if (emailKey != null) {
            increment(emailKey, window);
        }

        int remaining = remainingAttempts(ipKey, emailKey, settings);
        if (remaining <= 0) {
            boolean ipBlocked = ipKey != null && count(ipKey) >= settings.maxAttemptsPerIp();
            boolean emailBlocked = emailKey != null && count(emailKey) >= settings.maxAttemptsPerEmail();
            throw rateLimited(ipKey, emailKey, ipBlocked, emailBlocked, settings.windowMinutes());
        }
        throw unauthorized(
                remaining,
                retryAfterMinutesForKey(
                        tighterKey(ipKey, emailKey, settings), settings.windowMinutes()));
    }

    public void clearAttempts(HttpServletRequest request, String email) {
        String clientIp = clientIpResolver.resolve(request);
        String ipKey = ipKey(clientIp);
        String emailKey = emailKey(email);
        if (ipKey != null) {
            redisTemplate.delete(ipKey);
        }
        if (emailKey != null) {
            redisTemplate.delete(emailKey);
        }
    }

    private int remainingAttempts(
            String ipKey, String emailKey, LoginRateLimitSettingsResponse settings) {
        Integer ipRemaining = null;
        Integer emailRemaining = null;
        if (ipKey != null) {
            ipRemaining = Math.max(0, settings.maxAttemptsPerIp() - (int) count(ipKey));
        }
        if (emailKey != null) {
            emailRemaining = Math.max(0, settings.maxAttemptsPerEmail() - (int) count(emailKey));
        }
        if (ipRemaining == null && emailRemaining == null) {
            return Math.min(settings.maxAttemptsPerEmail(), settings.maxAttemptsPerIp());
        }
        if (ipRemaining == null) {
            return emailRemaining;
        }
        if (emailRemaining == null) {
            return ipRemaining;
        }
        return Math.min(ipRemaining, emailRemaining);
    }

    /**
     * Redis key for the cap that will block first. Email wins when remaining counts are equal.
     */
    private String tighterKey(
            String ipKey, String emailKey, LoginRateLimitSettingsResponse settings) {
        Integer ipRemaining = ipKey == null
                ? null
                : Math.max(0, settings.maxAttemptsPerIp() - (int) count(ipKey));
        Integer emailRemaining = emailKey == null
                ? null
                : Math.max(0, settings.maxAttemptsPerEmail() - (int) count(emailKey));
        if (emailRemaining == null) {
            return ipKey;
        }
        if (ipRemaining == null) {
            return emailKey;
        }
        return emailRemaining <= ipRemaining ? emailKey : ipKey;
    }

    private BusinessException rateLimited(
            String ipKey,
            String emailKey,
            boolean ipBlocked,
            boolean emailBlocked,
            int windowMinutes) {
        int retryAfterMinutes = retryAfterMinutes(ipKey, emailKey, ipBlocked, emailBlocked, windowMinutes);
        return new BusinessException(
                ErrorCode.RATE_LIMITED,
                "Too many login attempts. Try again in " + retryAfterMinutes + " minutes.",
                new LoginRateLimitErrorData(0, retryAfterMinutes));
    }

    private static BusinessException unauthorized(int remainingAttempts, int retryAfterMinutes) {
        return new BusinessException(
                ErrorCode.UNAUTHORIZED,
                "Invalid credentials. "
                        + remainingAttempts
                        + " attempts remaining. Window resets in "
                        + retryAfterMinutes
                        + " minutes.",
                new LoginRateLimitErrorData(remainingAttempts, retryAfterMinutes));
    }

    private int retryAfterMinutesForKey(String key, int windowMinutes) {
        if (key != null) {
            long ttl = ttlSeconds(key);
            if (ttl > 0) {
                return Math.max(1, (int) Math.ceil(ttl / 60.0));
            }
        }
        return Math.max(1, windowMinutes);
    }

    private int retryAfterMinutes(
            String ipKey,
            String emailKey,
            boolean ipBlocked,
            boolean emailBlocked,
            int windowMinutes) {
        long ttlSeconds = 0;
        if (ipBlocked && ipKey != null) {
            ttlSeconds = Math.max(ttlSeconds, ttlSeconds(ipKey));
        }
        if (emailBlocked && emailKey != null) {
            ttlSeconds = Math.max(ttlSeconds, ttlSeconds(emailKey));
        }
        if (ttlSeconds <= 0) {
            return Math.max(1, windowMinutes);
        }
        return Math.max(1, (int) Math.ceil(ttlSeconds / 60.0));
    }

    private long ttlSeconds(String key) {
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (ttl == null || ttl < 0) {
            return 0;
        }
        return ttl;
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

    private void increment(String key, Duration window) {
        Long next = redisTemplate.opsForValue().increment(key);
        if (next != null && next == 1L) {
            redisTemplate.expire(key, window);
        }
    }

    private static String ipKey(String clientIp) {
        return StringUtils.hasText(clientIp) ? IP_PREFIX + clientIp : null;
    }

    private static String emailKey(String email) {
        return StringUtils.hasText(email) ? EMAIL_PREFIX + normalizeEmail(email) : null;
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
