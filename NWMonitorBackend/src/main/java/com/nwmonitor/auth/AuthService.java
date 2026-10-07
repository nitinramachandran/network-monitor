package com.nwmonitor.auth;

import com.nwmonitor.alert.AlertService;
import com.nwmonitor.alert.AlertType;
import com.nwmonitor.alert.Severity;
import com.nwmonitor.auth.AuthDtos.TokenResponse;
import com.nwmonitor.common.ApiException;
import com.nwmonitor.common.NwmProperties;
import com.nwmonitor.security.JwtService;
import com.nwmonitor.user.User;
import com.nwmonitor.user.UserDtos.UserView;
import com.nwmonitor.user.UserRepository;
import com.nwmonitor.user.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AuthService {
    private static final int MAX_FAILURES = 5;
    private static final Duration LOCK = Duration.ofMinutes(15);
    private static final int IP_ALERT_THRESHOLD = 5;

    private final UserRepository users;
    private final UserService userService;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final NwmProperties props;
    private final LoginAttemptTracker attempts;
    private final AlertService alerts;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository users, UserService userService, RefreshTokenRepository refreshTokens, PasswordEncoder encoder,
                       JwtService jwt, NwmProperties props, LoginAttemptTracker attempts, AlertService alerts) {
        this.users = users;
        this.userService = userService;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.jwt = jwt;
        this.props = props;
        this.attempts = attempts;
        this.alerts = alerts;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse login(String username, String password, String clientIp) {
        User u = users.findByUsernameIgnoreCase(username.trim()).orElse(null);
        Instant now = Instant.now();
        if (u != null && u.getLockedUntil() != null && u.getLockedUntil().isAfter(now)) {
            throw new ApiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "Account temporarily locked");
        }
        if (u == null || !u.isEnabled() || !encoder.matches(password, u.getPasswordHash())) {
            if (u != null) {
                u.setFailedLogins(u.getFailedLogins() + 1);
                if (u.getFailedLogins() >= MAX_FAILURES) {
                    u.setLockedUntil(now.plus(LOCK));
                    u.setFailedLogins(0);
                }
            }
            int fromIp = attempts.recordFailure(clientIp);
            if (fromIp >= IP_ALERT_THRESHOLD) {
                alerts.raise(AlertType.LOGIN_BRUTE_FORCE, Severity.CRITICAL, "Repeated failed logins",
                        fromIp + " failed logins to the monitor app from " + clientIp + " in the last 10 minutes.",
                        null, clientIp, "login:" + clientIp);
            }
            throw ApiException.unauthorized("Invalid credentials");
        }
        attempts.clear(clientIp);
        u.setFailedLogins(0);
        u.setLockedUntil(null);
        u.setLastLoginAt(now);
        return issue(u);
    }

    @Transactional
    public TokenResponse refresh(String rawToken) {
        RefreshToken t = refreshTokens.findByTokenHash(hash(rawToken)).orElseThrow(() -> ApiException.unauthorized("Invalid refresh token"));
        if (t.isRevoked() || t.getExpiresAt().isBefore(Instant.now())) throw ApiException.unauthorized("Invalid refresh token");
        User u = users.findById(t.getUserId()).filter(User::isEnabled).orElseThrow(() -> ApiException.unauthorized("Invalid refresh token"));
        t.setRevoked(true); // rotation: each refresh token is single-use
        return issue(u);
    }

    @Transactional
    public void logout(String rawToken) {
        refreshTokens.findByTokenHash(hash(rawToken)).ifPresent(t -> t.setRevoked(true));
    }

    @Transactional
    public void changePassword(UUID userId, String current, String next) {
        User u = userService.get(userId);
        if (!encoder.matches(current, u.getPasswordHash())) throw ApiException.badRequest("Current password is incorrect");
        userService.setPassword(userId, next);
    }

    private TokenResponse issue(User u) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokens.save(new RefreshToken(u.getId(), hash(raw), Instant.now().plus(Duration.ofDays(props.security().refreshTokenDays()))));
        return new TokenResponse(jwt.issue(u), raw, jwt.ttlSeconds(), UserView.of(u));
    }

    private static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
