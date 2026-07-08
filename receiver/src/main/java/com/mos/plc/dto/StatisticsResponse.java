package com.mos.plc.dto;

import java.util.List;
import java.util.Map;

public record StatisticsResponse(
        long totalCount,
        long normalCount,
        long defectCount,
        double defectRate,
        long todayTotalCount,
        long todayDefectCount,
        double todayDefectRate,
        Map<String, Long> defectTypeCounts,
        List<TrendPoint> todayTrend
) {
    public record TrendPoint(String hour, long total, long defect) {
    }
}
