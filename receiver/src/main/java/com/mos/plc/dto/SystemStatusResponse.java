package com.mos.plc.dto;

import java.time.LocalDateTime;

public record SystemStatusResponse(
        int availableProcessors,
        double systemLoadAverage,
        long freeMemoryBytes,
        long totalMemoryBytes,
        long maxMemoryBytes,
        long diskFreeBytes,
        long diskTotalBytes,
        LocalDateTime serverTime
) {
}
