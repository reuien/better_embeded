package com.mos.plc.repository;

import com.mos.plc.entity.DetectionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.Optional;

public interface DetectionRecordRepository extends JpaRepository<DetectionRecord, Long>, JpaSpecificationExecutor<DetectionRecord> {
    Optional<DetectionRecord> findFirstByFilenameOrderByIdDesc(String filename);

    long countByDetectTimeBetween(LocalDateTime start, LocalDateTime end);

    long countByResultAndDetectTimeBetween(String result, LocalDateTime start, LocalDateTime end);
}
