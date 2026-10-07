package com.nwmonitor.alert;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<Alert, UUID>, JpaSpecificationExecutor<Alert> {
    long countByStatus(AlertStatus status);

    Optional<Alert> findFirstByDedupeKeyAndStatusAndLastOccurredAtAfterOrderByLastOccurredAtDesc(
            String dedupeKey, AlertStatus status, Instant after);

    @Modifying(clearAutomatically = true)
    @Query("update Alert a set a.status = 'ACKNOWLEDGED', a.acknowledgedBy = :by, a.acknowledgedAt = :at where a.status = 'OPEN'")
    int acknowledgeAll(@Param("by") String by, @Param("at") Instant at);
}
