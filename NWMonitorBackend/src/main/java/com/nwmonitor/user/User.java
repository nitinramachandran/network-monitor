package com.nwmonitor.user;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {
    @Id private UUID id;
    private String username;
    private String displayName;
    private String email;
    private String passwordHash;
    @Enumerated(EnumType.STRING) private Role role;
    /** "ROOT" for the single root user, null otherwise; the column is UNIQUE. */
    private String rootMarker;
    private boolean enabled = true;
    private int failedLogins;
    private Instant lockedUntil;
    private Instant createdAt;
    private Instant lastLoginAt;

    protected User() {}

    public User(String username, String displayName, String email, String passwordHash, Role role) {
        this.id = UUID.randomUUID();
        this.username = username;
        this.displayName = displayName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.rootMarker = role == Role.ROOT ? "ROOT" : null;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String v) { this.displayName = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String v) { this.passwordHash = v; }
    public Role getRole() { return role; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }
    public int getFailedLogins() { return failedLogins; }
    public void setFailedLogins(int v) { this.failedLogins = v; }
    public Instant getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(Instant v) { this.lockedUntil = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant v) { this.lastLoginAt = v; }
}
