package com.mos.plc.dto;

public record DeviceHeartbeatRequest(
        String hostName,
        String camera,
        String modelName
) {
}
