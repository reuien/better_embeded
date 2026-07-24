package com.mos.plc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "device_statuses")
public class DeviceStatus {
    @Id
    private String deviceId;

    @Column(nullable = false)
    private LocalDateTime lastHeartbeat;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private String status;
    private String hostName;
    private String camera;
    private String modelName;
}
