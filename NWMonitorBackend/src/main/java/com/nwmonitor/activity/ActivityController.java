package com.nwmonitor.activity;

import com.nwmonitor.common.PageResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/activity")
public class ActivityController {
    private final ActivityService service;

    public ActivityController(ActivityService service) { this.service = service; }

    /** Web activity (domains looked up) across all devices, filterable by device, day and domain text. */
    @GetMapping
    public PageResponse<ActivityService.ActivityView> list(@RequestParam(required = false) UUID deviceId,
                                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                           @RequestParam(required = false) String domain,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "50") int size) {
        return service.search(deviceId, date, domain, page, Math.min(size, 500));
    }

    @GetMapping("/top-domains")
    public List<ActivityService.TopDomain> topDomains(@RequestParam(required = false) UUID deviceId,
                                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                      @RequestParam(defaultValue = "10") int limit) {
        return service.topDomains(deviceId, date == null ? LocalDate.now() : date, Math.min(limit, 100));
    }
}
