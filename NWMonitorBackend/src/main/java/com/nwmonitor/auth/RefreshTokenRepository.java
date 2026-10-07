package com.nwmonitor.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true)
    @Query("update RefreshToken t set t.revoked = true where t.userId = :userId")
    void revokeAllForUser(@Param("userId") UUID userId);

    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :now")
    void deleteExpired(@Param("now") Instant now);
}
