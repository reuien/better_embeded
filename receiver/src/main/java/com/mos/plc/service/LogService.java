package com.mos.plc.service;

import com.mos.plc.dto.LogEntryResponse;
import com.mos.plc.entity.LogEntry;
import com.mos.plc.repository.LogEntryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class LogService {
    private final LogEntryRepository repository;

    public LogService(LogEntryRepository repository) {
        this.repository = repository;
    }

    public void info(String type, String message, String deviceId) {
        save("INFO", type, message, deviceId);
    }

    public void warn(String type, String message, String deviceId) {
        save("WARN", type, message, deviceId);
    }

    public void error(String type, String message, String deviceId) {
        save("ERROR", type, message, deviceId);
    }

    public Page<LogEntryResponse> search(String level, String keyword, LocalDateTime startTime, LocalDateTime endTime, Pageable pageable) {
        Specification<LogEntry> spec = Specification.where(null);
        if (level != null && !level.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("level"), level.trim().toUpperCase()));
        }
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            spec = spec.and((root, query, cb) -> cb.like(root.get("message"), like));
        }
        if (startTime != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), startTime));
        }
        if (endTime != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), endTime));
        }
        return repository.findAll(spec, pageable).map(LogEntryResponse::from);
    }

    private void save(String level, String type, String message, String deviceId) {
        LogEntry entry = new LogEntry();
        entry.setLevel(level);
        entry.setType(type);
        entry.setMessage(message);
        entry.setDeviceId(deviceId);
        entry.setCreatedAt(LocalDateTime.now());
        repository.save(entry);
    }
}
