package com.mos.plc.repository;

import com.mos.plc.entity.DetectionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.Optional;

public interface DetectionRecordRepository extends JpaRepository<DetectionRecord, Long>, JpaSpecificationExecutor<DetectionRecord> {
    Optional<DetectionRecord> findFirstByFilenameOrderByIdDesc(String filename);

    Optional<DetectionRecord> findFirstByOriginalFilenameOrderByIdDesc(String filename);

    Optional<DetectionRecord> findFirstByResultFilenameOrderByIdDesc(String filename);

    Optional<DetectionRecord> findFirstByDeviceIdAndPcbIdOrderByIdDesc(String deviceId, String pcbId);

    Optional<DetectionRecord> findFirstByOrderByDetectTimeDesc();

    long countByDetectTimeBetween(LocalDateTime start, LocalDateTime end);

    long countByResultAndDetectTimeBetween(String result, LocalDateTime start, LocalDateTime end);
}
