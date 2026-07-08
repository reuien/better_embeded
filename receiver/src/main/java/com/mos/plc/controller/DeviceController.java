package com.mos.plc.controller;

import com.mos.plc.dto.ApiResponse;
import com.mos.plc.dto.DeviceHeartbeatRequest;
import com.mos.plc.dto.DeviceStatusResponse;
import com.mos.plc.dto.SystemStatusResponse;
import com.mos.plc.service.DeviceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class DeviceController {
    private final DeviceService service;

    public DeviceController(DeviceService service) {
        this.service = service;
    }

    @GetMapping("/api/devices")
    public ApiResponse<List<DeviceStatusResponse>> devices() {
        return ApiResponse.success(service.listDevices());
    }

    @PutMapping("/api/devices/{deviceId}/heartbeat")
    public ApiResponse<DeviceStatusResponse> heartbeat(
            @PathVariable String deviceId,
            @RequestBody(required = false) DeviceHeartbeatRequest request
    ) {
        return ApiResponse.success(service.heartbeat(deviceId, request));
    }

    @GetMapping("/api/system-status")
    public ApiResponse<SystemStatusResponse> systemStatus() {
        return ApiResponse.success(service.systemStatus());
    }
}
