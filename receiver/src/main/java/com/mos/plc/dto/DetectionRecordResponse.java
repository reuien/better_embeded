package com.mos.plc.dto;

import com.mos.plc.entity.DetectionRecord;

import java.time.LocalDateTime;
import java.util.List;

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
        LocalDateTime createdAt,
        String pcbId,
        LocalDateTime captureTime,
        Integer defectCount,
        Integer inferenceTimeMs,
        String originalFilename,
        String originalImagePath,
        String originalImageUrl,
        String resultFilename,
        String resultImagePath,
        String resultImageUrl,
        List<DetectionDefectResponse> defects
) {
    public DetectionRecordResponse(
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
        this(
                id,
                filename,
                deviceId,
                result,
                defectType,
                confidence,
                imagePath,
                imageUrl,
                detectTime,
                createdAt,
                null,
                detectTime,
                result != null && result.equals("defect") ? 1 : 0,
                null,
                null,
                null,
                null,
                filename,
                imagePath,
                imageUrl,
                List.of()
        );
    }

    public static DetectionRecordResponse from(DetectionRecord record) {
        String displayFilename = firstNonBlank(record.getResultFilename(), record.getFilename(), record.getOriginalFilename());
        String displayImagePath = firstNonBlank(record.getResultImagePath(), record.getImagePath(), record.getOriginalImagePath());
        String resultFilename = firstNonBlank(record.getResultFilename(), record.getFilename());
        String originalFilename = record.getOriginalFilename();
        List<DetectionDefectResponse> defects = record.getDefects() == null
                ? List.of()
                : record.getDefects().stream().map(DetectionDefectResponse::from).toList();
        return new DetectionRecordResponse(
                record.getId(),
                displayFilename,
                record.getDeviceId(),
                record.getResult(),
                record.getDefectType(),
                record.getConfidence(),
                displayImagePath,
                imageUrl(displayFilename),
                record.getDetectTime(),
                record.getCreatedAt(),
                record.getPcbId(),
                record.getCaptureTime(),
                record.getDefectCount(),
                record.getInferenceTimeMs(),
                originalFilename,
                record.getOriginalImagePath(),
                imageUrl(originalFilename),
                resultFilename,
                firstNonBlank(record.getResultImagePath(), record.getImagePath()),
                imageUrl(resultFilename),
                defects
        );
    }

    private static String imageUrl(String filename) {
        return filename == null || filename.isBlank() ? null : "/api/images/" + filename;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
