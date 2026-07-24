package com.mos.plc.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "plc")
public class PlcProperties {
    private String uploadRoot = "uploads";
    private String frontendDir = "../front/dist";
    private long deviceOnlineSeconds = 60;
    private Replay replay = new Replay();

    @Getter
    @Setter
    public static class Replay {
        private boolean enabled = false;
        private long checkIntervalMs = 1000;
        private int maxActiveRecords = 100;
        private List<ScheduledRecord> scheduledRecords = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class ScheduledRecord {
        private boolean enabled = true;
        private String time;
        private Long delaySeconds;
        private Long recordId;
        private String filename;
        private String imagePath;
        private String deviceId = "SCHEDULED-REPLAY";
        private String result = "defect";
        private String defectType;
        private Double confidence;
    }
}
