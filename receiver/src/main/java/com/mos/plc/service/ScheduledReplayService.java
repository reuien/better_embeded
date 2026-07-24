package com.mos.plc.service;

import com.mos.plc.config.PlcProperties;
import com.mos.plc.dto.DetectionRecordResponse;
import com.mos.plc.entity.DetectionRecord;
import com.mos.plc.repository.DetectionRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ScheduledReplayService {
    private static final Logger log = LoggerFactory.getLogger(ScheduledReplayService.class);

    private final PlcProperties properties;
    private final DetectionRecordRepository repository;
    private final FileStorageService storageService;
    private final AtomicLong virtualIds = new AtomicLong(-1);
    private final LocalDateTime startedAt = LocalDateTime.now();
    private final Set<String> firedKeys = new HashSet<>();
    private final Set<String> warnedKeys = new HashSet<>();
    private final LinkedList<DetectionRecordResponse> activeRecords = new LinkedList<>();

    public ScheduledReplayService(
            PlcProperties properties,
            DetectionRecordRepository repository,
            FileStorageService storageService
    ) {
        this.properties = properties;
        this.repository = repository;
        this.storageService = storageService;
    }

    @Scheduled(fixedDelayString = "${plc.replay.check-interval-ms:1000}")
    public void activateDueRecords() {
        PlcProperties.Replay replay = properties.getReplay();
        if (replay == null || !replay.isEnabled() || replay.getScheduledRecords() == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        List<PlcProperties.ScheduledRecord> records = replay.getScheduledRecords();
        for (int index = 0; index < records.size(); index++) {
            PlcProperties.ScheduledRecord item = records.get(index);
            if (item == null || !item.isEnabled()) {
                continue;
            }
            String key = replayKey(index, item);
            Optional<LocalDateTime> dueTime = resolveDueTime(item, now, key);
            if (dueTime.isEmpty() || dueTime.get().isAfter(now)) {
                continue;
            }
            synchronized (this) {
                if (!firedKeys.add(key)) {
                    continue;
                }
            }
            activateRecord(item, now, key);
        }
    }

    public synchronized List<DetectionRecordResponse> activeRecords() {
        return new ArrayList<>(activeRecords);
    }

    public synchronized Optional<DetectionRecordResponse> getVirtualRecord(Long id) {
        if (id == null || id >= 0) {
            return Optional.empty();
        }
        return activeRecords.stream().filter(record -> id.equals(record.id())).findFirst();
    }

    public synchronized boolean removeVirtualRecord(Long id) {
        if (id == null || id >= 0) {
            return false;
        }
        return activeRecords.removeIf(record -> id.equals(record.id()));
    }

    private void activateRecord(PlcProperties.ScheduledRecord item, LocalDateTime activationTime, String key) {
        Optional<ReplayImageSource> source = resolveSource(item);
        if (source.isEmpty()) {
            log.warn(
                    "scheduled replay source not found key={} recordId={} filename={} imagePath={}",
                    key,
                    item.getRecordId(),
                    item.getFilename(),
                    item.getImagePath()
            );
            return;
        }

        ReplayImageSource image = source.get();
        String result = item.getResult() == null || item.getResult().isBlank()
                ? "defect"
                : ResultNormalizer.normalizeResult(item.getResult());
        String defectType = item.getDefectType() == null || item.getDefectType().isBlank()
                ? image.defectType()
                : item.getDefectType().trim();
        Double confidence = item.getConfidence() == null ? image.confidence() : item.getConfidence();
        String deviceId = item.getDeviceId() == null || item.getDeviceId().isBlank()
                ? "SCHEDULED-REPLAY"
                : item.getDeviceId().trim();

        DetectionRecordResponse response = new DetectionRecordResponse(
                virtualIds.getAndDecrement(),
                image.filename(),
                deviceId,
                result,
                defectType == null || defectType.isBlank() ? "scheduled-defect" : defectType,
                confidence == null ? 0.0 : confidence,
                image.path().toString(),
                "/api/images/" + image.filename(),
                activationTime,
                activationTime
        );

        synchronized (this) {
            activeRecords.addFirst(response);
            activeRecords.sort(Comparator.comparing(DetectionRecordResponse::detectTime).reversed());
            int maxActiveRecords = Math.max(1, properties.getReplay().getMaxActiveRecords());
            while (activeRecords.size() > maxActiveRecords) {
                activeRecords.removeLast();
            }
        }

        log.info("scheduled replay activated filename={} virtualId={} deviceId={}", image.filename(), response.id(), deviceId);
    }

    private Optional<ReplayImageSource> resolveSource(PlcProperties.ScheduledRecord item) {
        if (item.getRecordId() != null) {
            Optional<DetectionRecord> byId = repository.findById(item.getRecordId());
            if (byId.isPresent()) {
                return byId.map(ScheduledReplayService::fromRecord);
            }
        }
        if (item.getFilename() != null && !item.getFilename().isBlank()) {
            String filename = item.getFilename().trim();
            Optional<ReplayImageSource> fromDatabase = repository.findFirstByFilenameOrderByIdDesc(filename)
                    .map(ScheduledReplayService::fromRecord);
            if (fromDatabase.isPresent()) {
                return fromDatabase;
            }
            Path path = storageService.resolveExisting(filename);
            if (path != null) {
                return Optional.of(new ReplayImageSource(filename, path, null, null));
            }
        }
        if (item.getImagePath() != null && !item.getImagePath().isBlank()) {
            Path path = Path.of(item.getImagePath().trim()).toAbsolutePath().normalize();
            if (Files.exists(path) && Files.isRegularFile(path)) {
                return Optional.of(new ReplayImageSource(path.getFileName().toString(), path, null, null));
            }
        }

        return Optional.empty();
    }

    private Optional<LocalDateTime> resolveDueTime(PlcProperties.ScheduledRecord item, LocalDateTime now, String key) {
        if (item.getDelaySeconds() != null) {
            return Optional.of(startedAt.plusSeconds(Math.max(0, item.getDelaySeconds())));
        }
        return parseDueTime(item.getTime(), now, key);
    }

    private Optional<LocalDateTime> parseDueTime(String value, LocalDateTime now, String key) {
        if (value == null || value.isBlank()) {
            warnOnce(key, "scheduled replay time is empty");
            return Optional.empty();
        }

        String normalized = value.trim().replace(' ', 'T');
        try {
            return Optional.of(LocalDateTime.parse(normalized));
        } catch (DateTimeParseException ignored) {
            try {
                return Optional.of(LocalDateTime.of(now.toLocalDate(), LocalTime.parse(value.trim())));
            } catch (DateTimeParseException exc) {
                warnOnce(key, "scheduled replay time is invalid: " + value);
                return Optional.empty();
            }
        }
    }

    private synchronized void warnOnce(String key, String message) {
        if (warnedKeys.add(key)) {
            log.warn(message);
        }
    }

    private static String replayKey(int index, PlcProperties.ScheduledRecord item) {
        return index + "|"
                + item.getTime() + "|"
                + item.getDelaySeconds() + "|"
                + item.getRecordId() + "|"
                + item.getFilename() + "|"
                + item.getImagePath();
    }

    private static ReplayImageSource fromRecord(DetectionRecord record) {
        return new ReplayImageSource(
                record.getFilename(),
                Path.of(record.getImagePath()),
                record.getDefectType(),
                record.getConfidence()
        );
    }

    private record ReplayImageSource(String filename, Path path, String defectType, Double confidence) {
    }
}
