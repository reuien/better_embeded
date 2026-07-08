package com.mos.plc.service;

import com.mos.plc.dto.StatisticsResponse;
import com.mos.plc.entity.DetectionRecord;
import com.mos.plc.repository.DetectionRecordRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Service
public class StatisticsService {
    private final DetectionRecordRepository repository;

    public StatisticsService(DetectionRecordRepository repository) {
        this.repository = repository;
    }

    public StatisticsResponse current() {
        long total = repository.count();
        long defect = repository.count((root, query, cb) -> cb.equal(root.get("result"), "defect"));
        long normal = Math.max(0, total - defect);

        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        long todayTotal = repository.countByDetectTimeBetween(start, end);
        long todayDefect = repository.countByResultAndDetectTimeBetween("defect", start, end);

        Map<String, Long> typeCounts = defectTypeCounts();
        List<StatisticsResponse.TrendPoint> trend = todayTrend(start);

        return new StatisticsResponse(
                total,
                normal,
                defect,
                rate(defect, total),
                todayTotal,
                todayDefect,
                rate(todayDefect, todayTotal),
                typeCounts,
                trend
        );
    }

    private Map<String, Long> defectTypeCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        Specification<DetectionRecord> spec = (root, query, cb) -> cb.equal(root.get("result"), "defect");
        repository.findAll(spec).forEach(record -> {
            String type = record.getDefectType() == null || record.getDefectType().isBlank()
                    ? "unknown"
                    : record.getDefectType();
            counts.put(type, counts.getOrDefault(type, 0L) + 1);
        });
        return counts;
    }

    private List<StatisticsResponse.TrendPoint> todayTrend(LocalDateTime dayStart) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:00");
        return IntStream.range(0, 24)
                .mapToObj(hour -> {
                    LocalDateTime start = dayStart.plusHours(hour);
                    LocalDateTime end = start.plusHours(1);
                    long total = repository.countByDetectTimeBetween(start, end);
                    long defect = repository.countByResultAndDetectTimeBetween("defect", start, end);
                    return new StatisticsResponse.TrendPoint(start.format(formatter), total, defect);
                })
                .toList();
    }

    private static double rate(long numerator, long denominator) {
        if (denominator == 0) {
            return 0.0;
        }
        return Math.round((numerator * 10000.0 / denominator)) / 100.0;
    }
}
