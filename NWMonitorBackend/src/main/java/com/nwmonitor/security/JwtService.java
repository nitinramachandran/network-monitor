package com.nwmonitor.security;

import com.nwmonitor.common.NwmProperties;
import com.nwmonitor.user.Role;
import com.nwmonitor.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {
    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private final SecretKey key;
    private final Duration ttl;

    public JwtService(NwmProperties props) {
        String secret = props.security().jwtSecret();
        if (secret == null || secret.length() < 32) {
            log.warn("NWM_JWT_SECRET missing or shorter than 32 chars; using a random key (sessions reset on restart)");
            byte[] random = new byte[48];
            new SecureRandom().nextBytes(random);
            this.key = Keys.hmacShaKeyFor(random);
        } else {
            this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        }
        this.ttl = Duration.ofMinutes(props.security().accessTokenMinutes());
    }

    public long ttlSeconds() { return ttl.toSeconds(); }

    public String issue(User u) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(u.getId().toString())
                .claim("username", u.getUsername())
                .claim("role", u.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    public Optional<AppPrincipal> parse(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Optional.of(new AppPrincipal(UUID.fromString(c.getSubject()), c.get("username", String.class),
                    Role.valueOf(c.get("role", String.class))));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
