package com.mos.plc.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mos.plc.dto.DetectionRecordResponse;
import com.mos.plc.entity.DetectionDefect;
import com.mos.plc.entity.DetectionRecord;
import com.mos.plc.exception.ConflictException;
import com.mos.plc.exception.NotFoundException;
import com.mos.plc.repository.DetectionRecordRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class DetectionRecordService {
    private final DetectionRecordRepository repository;
    private final FileStorageService storageService;
    private final LogService logService;
    private final ScheduledReplayService replayService;
    private final ObjectMapper objectMapper;

    public DetectionRecordService(
            DetectionRecordRepository repository,
            FileStorageService storageService,
            LogService logService,
            ScheduledReplayService replayService,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.storageService = storageService;
        this.logService = logService;
        this.replayService = replayService;
        this.objectMapper = objectMapper;
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
        record.setDefectCount(normalizedResult.equals("defect") ? 1 : 0);
        record.setImagePath(storedImage.path().toString());
        record.setResultFilename(storedImage.filename());
        record.setResultImagePath(storedImage.path().toString());
        record.setDetectTime(effectiveTime);
        record.setCaptureTime(effectiveTime);
        record.setCreatedAt(LocalDateTime.now());
        DetectionRecord saved = repository.save(record);
        logService.info("UPLOAD", "Detection image uploaded: " + saved.getFilename(), saved.getDeviceId());
        return DetectionRecordResponse.from(saved);
    }

    @Transactional
    public DetectionRecordResponse createBoardUpload(
            MultipartFile originalImage,
            MultipartFile resultImage,
            String deviceId,
            String pcbId,
            String captureTime,
            String result,
            Integer inferenceTimeMs,
            String defectsJson
    ) throws IOException {
        String effectiveDeviceId = required(deviceId, "device_id");
        String effectivePcbId = required(pcbId, "pcb_id");
        if (repository.findFirstByDeviceIdAndPcbIdOrderByIdDesc(effectiveDeviceId, effectivePcbId).isPresent()) {
            throw new ConflictException("Duplicate detection record: device_id=" + effectiveDeviceId + ", pcb_id=" + effectivePcbId);
        }

        LocalDateTime effectiveTime = parseDateTime(captureTime).orElseGet(LocalDateTime::now);
        String normalizedResult = ResultNormalizer.normalizeResult(result);
        List<DetectionDefect> defects = parseDefects(defectsJson);
        validateDefectsForResult(normalizedResult, defects);
        String defectType = summarizeDefectType(defects);
        Double confidence = summarizeConfidence(defects);

        FileStorageService.StoredImage storedOriginal = null;
        FileStorageService.StoredImage storedResult = null;
        try {
            storedOriginal = storageService.store(
                    originalImage,
                    normalizedResult,
                    effectiveTime.toLocalDate(),
                    "original"
            );
            storedResult = storageService.store(
                    resultImage,
                    normalizedResult,
                    effectiveTime.toLocalDate(),
                    "result"
            );

            DetectionRecord record = new DetectionRecord();
            record.setFilename(storedResult.filename());
            record.setDeviceId(effectiveDeviceId);
            record.setPcbId(effectivePcbId);
            record.setResult(normalizedResult);
            record.setDefectType(defectType);
            record.setConfidence(confidence);
            record.setDefectCount(defects.size());
            record.setInferenceTimeMs(inferenceTimeMs);
            record.setOriginalFilename(storedOriginal.filename());
            record.setOriginalImagePath(storedOriginal.path().toString());
            record.setResultFilename(storedResult.filename());
            record.setResultImagePath(storedResult.path().toString());
            record.setImagePath(storedResult.path().toString());
            record.setCaptureTime(effectiveTime);
            record.setDetectTime(effectiveTime);
            record.setCreatedAt(LocalDateTime.now());
            record.setDefects(defects);

            DetectionRecord saved = repository.saveAndFlush(record);
            logService.info(
                    "BOARD_UPLOAD",
                    "Board detection uploaded: pcb_id=" + effectivePcbId + ", result=" + normalizedResult,
                    effectiveDeviceId
            );
            return DetectionRecordResponse.from(saved);
        } catch (DataIntegrityViolationException exc) {
            deleteStoredImage(storedOriginal);
            deleteStoredImage(storedResult);
            throw new ConflictException("Duplicate detection record: device_id=" + effectiveDeviceId + ", pcb_id=" + effectivePcbId);
        } catch (IOException | RuntimeException exc) {
            deleteStoredImage(storedOriginal);
            deleteStoredImage(storedResult);
            throw exc;
        }
    }

    @Transactional(readOnly = true)
    public Page<DetectionRecordResponse> search(
            LocalDateTime startTime,
            LocalDateTime endTime,
            String deviceId,
            String pcbId,
            String defectType,
            String result,
            Pageable pageable
    ) {
        Specification<DetectionRecord> spec = buildSpec(startTime, endTime, deviceId, pcbId, defectType, result);
        List<DetectionRecordResponse> replayRecords = replayService.activeRecords().stream()
                .filter(record -> matches(record, startTime, endTime, deviceId, pcbId, defectType, result))
                .toList();
        if (replayRecords.isEmpty()) {
            return repository.findAll(spec, pageable).map(DetectionRecordResponse::from);
        }

        List<DetectionRecordResponse> merged = repository
                .findAll(spec, Sort.by(Sort.Direction.DESC, "detectTime"))
                .stream()
                .map(DetectionRecordResponse::from)
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        merged.addAll(replayRecords);
        merged.sort(Comparator.comparing(DetectionRecordResponse::detectTime).reversed());

        int start = Math.toIntExact(Math.min(pageable.getOffset(), merged.size()));
        int end = Math.min(start + pageable.getPageSize(), merged.size());
        return new PageImpl<>(merged.subList(start, end), pageable, merged.size());
    }

    private Specification<DetectionRecord> buildSpec(
            LocalDateTime startTime,
            LocalDateTime endTime,
            String deviceId,
            String pcbId,
            String defectType,
            String result
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
        if (pcbId != null && !pcbId.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("pcbId"), pcbId.trim()));
        }
        if (defectType != null && !defectType.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.like(root.get("defectType"), "%" + defectType.trim() + "%"));
        }
        if (result != null && !result.isBlank()) {
            String normalizedResult = ResultNormalizer.normalizeResult(result);
            spec = spec.and((root, query, cb) -> cb.equal(root.get("result"), normalizedResult));
        }
        return spec;
    }

    private boolean matches(
            DetectionRecordResponse record,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String deviceId,
            String pcbId,
            String defectType,
            String result
    ) {
        if (startTime != null && record.detectTime().isBefore(startTime)) {
            return false;
        }
        if (endTime != null && record.detectTime().isAfter(endTime)) {
            return false;
        }
        if (deviceId != null && !deviceId.isBlank() && !record.deviceId().equals(deviceId.trim())) {
            return false;
        }
        if (pcbId != null && !pcbId.isBlank() && !pcbId.trim().equals(record.pcbId())) {
            return false;
        }
        if (defectType != null && !defectType.isBlank()) {
            String recordDefectType = record.defectType() == null ? "" : record.defectType();
            if (!recordDefectType.contains(defectType.trim())) {
                return false;
            }
        }
        if (result != null && !result.isBlank()) {
            return record.result().equals(ResultNormalizer.normalizeResult(result));
        }
        return true;
    }

    @Transactional(readOnly = true)
    public DetectionRecordResponse latest() {
        return repository.findFirstByOrderByDetectTimeDesc()
                .map(DetectionRecordResponse::from)
                .orElseThrow(() -> new NotFoundException("Detection record not found"));
    }

    @Transactional(readOnly = true)
    public DetectionRecord getEntity(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Detection record not found: " + id));
    }

    @Transactional(readOnly = true)
    public DetectionRecordResponse get(Long id) {
        if (id != null && id < 0) {
            return replayService.getVirtualRecord(id)
                    .orElseThrow(() -> new NotFoundException("Detection record not found: " + id));
        }
        return DetectionRecordResponse.from(getEntity(id));
    }

    @Transactional
    public void delete(Long id) {
        if (replayService.removeVirtualRecord(id)) {
            return;
        }
        DetectionRecord record = getEntity(id);
        repository.delete(record);
        storageService.deleteIfExists(record.getImagePath());
        storageService.deleteIfExists(record.getOriginalImagePath());
        storageService.deleteIfExists(record.getResultImagePath());
        logService.warn("DELETE", "Detection record deleted: " + id, record.getDeviceId());
    }

    private List<DetectionDefect> parseDefects(String defectsJson) throws IOException {
        if (defectsJson == null || defectsJson.isBlank()) {
            return List.of();
        }
        JsonNode root = objectMapper.readTree(defectsJson);
        JsonNode defectsNode = root.isArray() ? root : root.get("defects");
        if (defectsNode == null || !defectsNode.isArray()) {
            throw new IllegalArgumentException("defects must be a JSON array or an object containing defects[]");
        }

        List<DetectionDefect> defects = new ArrayList<>();
        int index = 0;
        for (JsonNode node : defectsNode) {
            int defectIndex = index;
            if (!node.isObject()) {
                throw new IllegalArgumentException("defects[" + defectIndex + "] must be a JSON object");
            }
            DetectionDefect defect = new DetectionDefect();
            defect.setClassName(firstText(node, "class_name", "className", "name", "label", "type")
                    .orElseThrow(() -> new IllegalArgumentException("defects[" + defectIndex + "].class_name is required")));
            Double confidence = firstDouble(node, "confidence", "conf", "score")
                    .orElseThrow(() -> new IllegalArgumentException("defects[" + defectIndex + "].confidence is required"));
            if (confidence < 0.0 || confidence > 1.0) {
                throw new IllegalArgumentException("defects[" + defectIndex + "].confidence must be between 0 and 1");
            }
            defect.setConfidence(confidence);

            JsonNode bbox = firstArray(node, "bbox", "box", "xyxy").orElse(null);
            if (bbox != null && bbox.size() >= 4) {
                defect.setX1(asDouble(bbox.get(0)).orElse(null));
                defect.setY1(asDouble(bbox.get(1)).orElse(null));
                defect.setX2(asDouble(bbox.get(2)).orElse(null));
                defect.setY2(asDouble(bbox.get(3)).orElse(null));
            } else {
                defect.setX1(firstDouble(node, "x1", "left").orElse(null));
                defect.setY1(firstDouble(node, "y1", "top").orElse(null));
                defect.setX2(firstDouble(node, "x2", "right").orElse(null));
                defect.setY2(firstDouble(node, "y2", "bottom").orElse(null));
            }
            if (defect.getX1() == null || defect.getY1() == null || defect.getX2() == null || defect.getY2() == null) {
                throw new IllegalArgumentException("defects[" + defectIndex + "].bbox must contain four numeric values");
            }
            defects.add(defect);
            index++;
        }
        return defects;
    }

    private static void validateDefectsForResult(String normalizedResult, List<DetectionDefect> defects) {
        if (ResultNormalizer.isDefect(normalizedResult) && defects.isEmpty()) {
            throw new IllegalArgumentException("defects is required when result is NG or defect");
        }
        if (ResultNormalizer.isNormal(normalizedResult) && !defects.isEmpty()) {
            throw new IllegalArgumentException("defects must be empty when result is OK or normal");
        }
    }

    private void deleteStoredImage(FileStorageService.StoredImage image) {
        if (image != null) {
            storageService.deleteIfExists(image.path().toString());
        }
    }

    private static String summarizeDefectType(List<DetectionDefect> defects) {
        if (defects.isEmpty()) {
            return "none";
        }
        return defects.stream()
                .map(DetectionDefect::getClassName)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .reduce((left, right) -> left + "," + right)
                .orElse("unknown");
    }

    private static Double summarizeConfidence(List<DetectionDefect> defects) {
        return defects.stream()
                .map(DetectionDefect::getConfidence)
                .filter(value -> value != null)
                .max(Double::compareTo)
                .orElse(0.0);
    }

    private static Optional<LocalDateTime> parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        for (DateTimeFormatter formatter : List.of(
                DateTimeFormatter.ISO_DATE_TIME,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
        )) {
            try {
                return Optional.of(LocalDateTime.parse(trimmed, formatter));
            } catch (DateTimeParseException ignored) {
            }
        }
        throw new IllegalArgumentException("capture_time must be ISO datetime or yyyy-MM-dd HH:mm:ss");
    }

    private static Optional<JsonNode> firstArray(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && value.isArray()) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    private static Optional<String> firstText(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) {
                String text = value.asText();
                if (!text.isBlank()) {
                    return Optional.of(text.trim());
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<Double> firstDouble(JsonNode node, String... names) {
        for (String name : names) {
            Optional<Double> value = asDouble(node.get(name));
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    private static Optional<Double> asDouble(JsonNode node) {
        if (node == null || node.isNull()) {
            return Optional.empty();
        }
        if (node.isNumber()) {
            return Optional.of(node.asDouble());
        }
        if (node.isTextual() && !node.asText().isBlank()) {
            try {
                return Optional.of(Double.parseDouble(node.asText().trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return Optional.empty();
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
