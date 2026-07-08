package com.mos.plc.dto;

import com.mos.plc.entity.DeviceStatus;

import java.time.Duration;
import java.time.LocalDateTime;

public record DeviceStatusResponse(
        String deviceId,
        String status,
        boolean online,
        String hostName,
        String camera,
        String modelName,
        LocalDateTime lastHeartbeat,
        LocalDateTime updatedAt
) {
    public static DeviceStatusResponse from(DeviceStatus status, long onlineSeconds) {
        boolean online = status.getLastHeartbeat() != null
                && Duration.between(status.getLastHeartbeat(), LocalDateTime.now()).getSeconds() <= onlineSeconds;
        return new DeviceStatusResponse(
                status.getDeviceId(),
                online ? "online" : "offline",
                online,
                status.getHostName(),
                status.getCamera(),
                status.getModelName(),
                status.getLastHeartbeat(),
                status.getUpdatedAt()
        );
    }
}
