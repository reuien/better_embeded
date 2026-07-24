package com.mos.plc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(
        name = "detection_records",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_detection_device_pcb", columnNames = {"deviceId", "pcbId"})
        },
        indexes = {
                @Index(name = "idx_detection_device_time", columnList = "deviceId,detectTime"),
                @Index(name = "idx_detection_result_time", columnList = "result,detectTime"),
                @Index(name = "idx_detection_defect_type", columnList = "defectType"),
                @Index(name = "idx_detection_device_pcb", columnList = "deviceId,pcbId")
        }
)
public class DetectionRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false)
    private String deviceId;

    private String pcbId;

    @Column(nullable = false)
    private String result;

    private String defectType;

    private Double confidence;

    private Integer defectCount;

    private Integer inferenceTimeMs;

    private String originalFilename;

    private String originalImagePath;

    private String resultFilename;

    private String resultImagePath;

    @Column(nullable = false)
    private String imagePath;

    @Column(nullable = false)
    private LocalDateTime detectTime;

    private LocalDateTime captureTime;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Setter(lombok.AccessLevel.NONE)
    @OneToMany(mappedBy = "record", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetectionDefect> defects = new ArrayList<>();

    public void setDefects(List<DetectionDefect> defects) {
        this.defects.clear();
        if (defects != null) {
            defects.forEach(this::addDefect);
        }
    }

    public void addDefect(DetectionDefect defect) {
        defect.setRecord(this);
        this.defects.add(defect);
    }
}
