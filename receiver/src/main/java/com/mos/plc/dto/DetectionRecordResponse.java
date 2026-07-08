package com.mos.plc.dto;

import com.mos.plc.entity.DetectionRecord;

import java.time.LocalDateTime;

public record DetectionRecordResponse(
        Long id,
        String filename,
        String deviceId,
        String result,
        String defectType,
        Double confidence,
        String imagePath,
        String imageUrl,
        LocalDateTime detectTime,
        LocalDateTime createdAt
) {
    public static DetectionRecordResponse from(DetectionRecord record) {
        return new DetectionRecordResponse(
                record.getId(),
                record.getFilename(),
                record.getDeviceId(),
                record.getResult(),
                record.getDefectType(),
                record.getConfidence(),
                record.getImagePath(),
                "/api/images/" + record.getFilename(),
                record.getDetectTime(),
                record.getCreatedAt()
        );
    }
}
