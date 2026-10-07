package com.nwmonitor.auth;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id private UUID id;
    private UUID userId;
    private String tokenHash;
    private Instant expiresAt;
    private boolean revoked;

    protected RefreshToken() {}

    public RefreshToken(UUID userId, String tokenHash, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public UUID getUserId() { return userId; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isRevoked() { return revoked; }
    public void setRevoked(boolean v) { this.revoked = v; }
}
