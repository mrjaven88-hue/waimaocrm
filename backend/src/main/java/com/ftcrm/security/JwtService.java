package com.ftcrm.security;

import com.ftcrm.config.AppProperties;
import com.ftcrm.domain.AppUser;
import com.ftcrm.time.TimeSource;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final Duration ttl;
    private final TimeSource time;

    public JwtService(AppProperties properties, TimeSource time) {
        String secret = properties.getJwt().getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
        }
        int hours = properties.getJwt().getTtlHours();
        if (hours < 1 || hours > 24) {
            throw new IllegalStateException("ftcrm.jwt.ttl-hours must be 1..24");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = Duration.ofHours(hours);
        this.time = time;
    }

    public String issue(AppUser user) {
        Instant now = time.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("username", user.getUsername())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        // 校验用同一 TimeSource，避免测试可调时钟与墙钟漂移导致误判过期。
        return Jwts.parser()
                .verifyWith(key)
                .clock(() -> Date.from(time.now()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
