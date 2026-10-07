package com.nwmonitor.usage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DailyUsageRepository extends JpaRepository<DailyUsage, Long> {
    Optional<DailyUsage> findByDeviceIdAndUsageDate(UUID deviceId, LocalDate date);
    List<DailyUsage> findByUsageDate(LocalDate date);
    List<DailyUsage> findByDeviceIdAndUsageDateBetweenOrderByUsageDate(UUID deviceId, LocalDate from, LocalDate to);

    interface DayTotal {
        LocalDate getUsageDate();
        Long getBytesDown();
        Long getBytesUp();
    }

    @Query("select u.usageDate as usageDate, sum(u.bytesDown) as bytesDown, sum(u.bytesUp) as bytesUp " +
           "from DailyUsage u where u.usageDate between :from and :to group by u.usageDate order by u.usageDate")
    List<DayTotal> totalsPerDay(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
