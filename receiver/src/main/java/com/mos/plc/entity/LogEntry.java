package com.mos.plc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
        name = "log_entries",
        indexes = {
                @Index(name = "idx_log_level_time", columnList = "level,createdAt"),
                @Index(name = "idx_log_type_time", columnList = "type,createdAt")
        }
)
public class LogEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String level;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false, length = 1000)
    private String message;

    private String deviceId;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
