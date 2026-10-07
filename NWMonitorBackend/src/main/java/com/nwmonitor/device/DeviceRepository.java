package com.nwmonitor.device;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceRepository extends JpaRepository<Device, UUID>, JpaSpecificationExecutor<Device> {
    Optional<Device> findByMac(String mac);
    Optional<Device> findFirstByIpOrderByLastSeenDesc(String ip);
    List<Device> findByIpAndStatusAndMacNot(String ip, DeviceStatus status, String mac);
    List<Device> findByStatusAndLastSeenBefore(DeviceStatus status, Instant before);
    long countByStatus(DeviceStatus status);
    long countByTrusted(boolean trusted);
}
