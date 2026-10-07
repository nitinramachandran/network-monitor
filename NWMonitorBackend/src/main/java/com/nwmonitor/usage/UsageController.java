package com.nwmonitor.usage;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/usage")
public class UsageController {
    private final UsageService service;

    public UsageController(UsageService service) { this.service = service; }

    /** Every device's data consumption (bytes and MB) for one day; defaults to today. */
    @GetMapping("/daily")
    public List<UsageService.DeviceUsage> daily(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.perDevice(date == null ? service.today() : date);
    }

    /** Whole-network totals per day for a date range. */
    @GetMapping("/totals")
    public List<UsageService.DayUsage> totals(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.totalsPerDay(from, to);
    }
}
