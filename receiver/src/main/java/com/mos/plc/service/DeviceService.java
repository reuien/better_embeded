package com.mos.plc.service;

import com.mos.plc.config.PlcProperties;
import com.mos.plc.dto.DeviceHeartbeatRequest;
import com.mos.plc.dto.DeviceStatusResponse;
import com.mos.plc.dto.SystemStatusResponse;
import com.mos.plc.entity.DeviceStatus;
import com.mos.plc.repository.DeviceStatusRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class DeviceService {
    private final DeviceStatusRepository repository;
    private final PlcProperties properties;
    private final LogService logService;

    public DeviceService(DeviceStatusRepository repository, PlcProperties properties, LogService logService) {
        this.repository = repository;
        this.properties = properties;
        this.logService = logService;
    }

    public List<DeviceStatusResponse> listDevices() {
        return repository.findAll().stream()
                .map(status -> DeviceStatusResponse.from(status, properties.getDeviceOnlineSeconds()))
                .toList();
    }

    @Transactional
    public DeviceStatusResponse heartbeat(String deviceId, DeviceHeartbeatRequest request) {
        DeviceStatus status = repository.findById(deviceId).orElseGet(DeviceStatus::new);
        LocalDateTime now = LocalDateTime.now();
        status.setDeviceId(deviceId);
        status.setLastHeartbeat(now);
        status.setUpdatedAt(now);
        status.setStatus("online");
        if (request != null) {
            status.setHostName(request.hostName());
            status.setCamera(request.camera());
            status.setModelName(request.modelName());
        }
        DeviceStatus saved = repository.save(status);
        logService.info("HEARTBEAT", "Device heartbeat: " + deviceId, deviceId);
        return DeviceStatusResponse.from(saved, properties.getDeviceOnlineSeconds());
    }

    public SystemStatusResponse systemStatus() {
        Runtime runtime = Runtime.getRuntime();
        File root = new File(".");
        return new SystemStatusResponse(
                runtime.availableProcessors(),
                java.lang.management.ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage(),
                runtime.freeMemory(),
                runtime.totalMemory(),
                runtime.maxMemory(),
                root.getFreeSpace(),
                root.getTotalSpace(),
                LocalDateTime.now()
        );
    }
}
