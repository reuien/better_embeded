package com.mos.plc.dto;

import com.mos.plc.entity.DetectionDefect;

public record DetectionDefectResponse(
        Long id,
        String className,
        Double confidence,
        Double x1,
        Double y1,
        Double x2,
        Double y2
) {
    public static DetectionDefectResponse from(DetectionDefect defect) {
        return new DetectionDefectResponse(
                defect.getId(),
                defect.getClassName(),
                defect.getConfidence(),
                defect.getX1(),
                defect.getY1(),
                defect.getX2(),
                defect.getY2()
        );
    }
}
