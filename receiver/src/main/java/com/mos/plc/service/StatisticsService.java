package com.mos.plc.service;

import com.mos.plc.dto.DetectionRecordResponse;
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
    private final ScheduledReplayService replayService;

    public StatisticsService(DetectionRecordRepository repository, ScheduledReplayService replayService) {
        this.repository = repository;
        this.replayService = replayService;
    }

    public StatisticsResponse current() {
        List<DetectionRecordResponse> replayRecords = replayService.activeRecords();
        long total = repository.count();
        long defect = repository.count((root, query, cb) -> cb.equal(root.get("result"), "defect"));
        long replayTotal = replayRecords.size();
        long replayDefect = replayRecords.stream().filter(StatisticsService::isDefect).count();
        total += replayTotal;
        defect += replayDefect;
        long normal = Math.max(0, total - defect);

        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        long todayTotal = repository.countByDetectTimeBetween(start, end);
        long todayDefect = repository.countByResultAndDetectTimeBetween("defect", start, end);
        long replayTodayTotal = replayRecords.stream()
                .filter(record -> !record.detectTime().isBefore(start) && record.detectTime().isBefore(end))
                .count();
        long replayTodayDefect = replayRecords.stream()
                .filter(record -> !record.detectTime().isBefore(start) && record.detectTime().isBefore(end))
                .filter(StatisticsService::isDefect)
                .count();
        todayTotal += replayTodayTotal;
        todayDefect += replayTodayDefect;

        Map<String, Long> typeCounts = defectTypeCounts(replayRecords);
        List<StatisticsResponse.TrendPoint> trend = todayTrend(start, replayRecords);

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

    private Map<String, Long> defectTypeCounts(List<DetectionRecordResponse> replayRecords) {
        Map<String, Long> counts = new LinkedHashMap<>();
        Specification<DetectionRecord> spec = (root, query, cb) -> cb.equal(root.get("result"), "defect");
        repository.findAll(spec).forEach(record -> {
            String type = record.getDefectType() == null || record.getDefectType().isBlank()
                    ? "unknown"
                    : record.getDefectType();
            counts.put(type, counts.getOrDefault(type, 0L) + 1);
        });
        replayRecords.stream().filter(StatisticsService::isDefect).forEach(record -> {
            String type = record.defectType() == null || record.defectType().isBlank()
                    ? "unknown"
                    : record.defectType();
            counts.put(type, counts.getOrDefault(type, 0L) + 1);
        });
        return counts;
    }

    private List<StatisticsResponse.TrendPoint> todayTrend(LocalDateTime dayStart, List<DetectionRecordResponse> replayRecords) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:00");
        return IntStream.range(0, 24)
                .mapToObj(hour -> {
                    LocalDateTime start = dayStart.plusHours(hour);
                    LocalDateTime end = start.plusHours(1);
                    long total = repository.countByDetectTimeBetween(start, end);
                    long defect = repository.countByResultAndDetectTimeBetween("defect", start, end);
                    long replayTotal = replayRecords.stream()
                            .filter(record -> !record.detectTime().isBefore(start) && record.detectTime().isBefore(end))
                            .count();
                    long replayDefect = replayRecords.stream()
                            .filter(record -> !record.detectTime().isBefore(start) && record.detectTime().isBefore(end))
                            .filter(StatisticsService::isDefect)
                            .count();
                    total += replayTotal;
                    defect += replayDefect;
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

    private static boolean isDefect(DetectionRecordResponse record) {
        return "defect".equals(record.result());
    }
}
