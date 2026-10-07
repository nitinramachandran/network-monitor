package com.nwmonitor.activity;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface WebActivityRepository extends JpaRepository<WebActivity, Long>, JpaSpecificationExecutor<WebActivity> {

    interface DomainCount {
        String getDomain();
        Long getHits();
    }

    @Query("select w.domain as domain, count(w) as hits from WebActivity w where w.queriedAt >= :from and w.queriedAt < :to " +
           "group by w.domain order by count(w) desc")
    List<DomainCount> topDomains(@Param("from") Instant from, @Param("to") Instant to, Pageable page);

    @Query("select w.domain as domain, count(w) as hits from WebActivity w where w.deviceId = :deviceId and w.queriedAt >= :from " +
           "and w.queriedAt < :to group by w.domain order by count(w) desc")
    List<DomainCount> topDomainsForDevice(@Param("deviceId") UUID deviceId, @Param("from") Instant from, @Param("to") Instant to, Pageable page);

    @Modifying
    @Query("delete from WebActivity w where w.queriedAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
