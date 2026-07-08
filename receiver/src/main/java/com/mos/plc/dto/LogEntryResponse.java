package com.mos.plc.dto;

import com.mos.plc.entity.LogEntry;

import java.time.LocalDateTime;

public record LogEntryResponse(
        Long id,
        String level,
        String type,
        String message,
        String deviceId,
        LocalDateTime createdAt
) {
    public static LogEntryResponse from(LogEntry entry) {
        return new LogEntryResponse(
                entry.getId(),
                entry.getLevel(),
                entry.getType(),
                entry.getMessage(),
                entry.getDeviceId(),
                entry.getCreatedAt()
        );
    }
}
