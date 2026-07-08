package com.mos.plc.service;

import com.mos.plc.dto.DetectionRecordResponse;
import com.mos.plc.entity.DetectionRecord;
import com.mos.plc.exception.NotFoundException;
import com.mos.plc.repository.DetectionRecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class DetectionRecordService {
    private final DetectionRecordRepository repository;
    private final FileStorageService storageService;
    private final LogService logService;

    public DetectionRecordService(
            DetectionRecordRepository repository,
            FileStorageService storageService,
            LogService logService
    ) {
        this.repository = repository;
        this.storageService = storageService;
        this.logService = logService;
    }

    @Transactional
    public DetectionRecordResponse create(
            MultipartFile image,
            String deviceId,
            String result,
            String defectType,
            Double confidence,
            LocalDateTime detectTime
    ) throws IOException {
        LocalDateTime effectiveTime = detectTime == null ? LocalDateTime.now() : detectTime;
        String normalizedResult = ResultNormalizer.normalizeResult(result);
        FileStorageService.StoredImage storedImage = storageService.store(image, normalizedResult, effectiveTime.toLocalDate());

        DetectionRecord record = new DetectionRecord();
        record.setFilename(storedImage.filename());
        record.setDeviceId(required(deviceId, "deviceId"));
        record.setResult(normalizedResult);
        record.setDefectType(defectType == null || defectType.isBlank() ? "none" : defectType.trim());
        record.setConfidence(confidence == null ? 0.0 : confidence);
        record.setImagePath(storedImage.path().toString());
        record.setDetectTime(effectiveTime);
        record.setCreatedAt(LocalDateTime.now());
        DetectionRecord saved = repository.save(record);
        logService.info("UPLOAD", "Detection image uploaded: " + saved.getFilename(), saved.getDeviceId());
        return DetectionRecordResponse.from(saved);
    }

    public Page<DetectionRecordResponse> search(
            LocalDateTime startTime,
            LocalDateTime endTime,
            String deviceId,
            String defectType,
            String result,
            Pageable pageable
    ) {
        Specification<DetectionRecord> spec = Specification.where(null);
        if (startTime != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("detectTime"), startTime));
        }
        if (endTime != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("detectTime"), endTime));
        }
        if (deviceId != null && !deviceId.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("deviceId"), deviceId.trim()));
        }
        if (defectType != null && !defectType.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.like(root.get("defectType"), "%" + defectType.trim() + "%"));
        }
        if (result != null && !result.isBlank()) {
            String normalizedResult = ResultNormalizer.normalizeResult(result);
            spec = spec.and((root, query, cb) -> cb.equal(root.get("result"), normalizedResult));
        }
        return repository.findAll(spec, pageable).map(DetectionRecordResponse::from);
    }

    public DetectionRecord getEntity(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Detection record not found: " + id));
    }

    public DetectionRecordResponse get(Long id) {
        return DetectionRecordResponse.from(getEntity(id));
    }

    @Transactional
    public void delete(Long id) {
        DetectionRecord record = getEntity(id);
        repository.delete(record);
        storageService.deleteIfExists(record.getImagePath());
        logService.warn("DELETE", "Detection record deleted: " + id, record.getDeviceId());
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
