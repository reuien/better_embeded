package com.mos.plc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "detection_defects",
        indexes = {
                @Index(name = "idx_defect_record", columnList = "record_id"),
                @Index(name = "idx_defect_class_name", columnList = "className")
        }
)
public class DetectionDefect {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "record_id", nullable = false)
    private DetectionRecord record;

    @Column(nullable = false)
    private String className;

    private Double confidence;

    private Double x1;

    private Double y1;

    private Double x2;

    private Double y2;
}
