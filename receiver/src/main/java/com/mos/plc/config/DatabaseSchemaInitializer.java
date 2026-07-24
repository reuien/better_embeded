package com.mos.plc.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class DatabaseSchemaInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    public DatabaseSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureBoardIdentityIsUnique();
    }

    private void ensureBoardIdentityIsUnique() {
        List<Map<String, Object>> duplicates = jdbcTemplate.queryForList("""
                select device_id, pcb_id, count(*) as duplicate_count
                from detection_records
                where pcb_id is not null and trim(pcb_id) <> ''
                group by device_id, pcb_id
                having count(*) > 1
                limit 1
                """);
        if (!duplicates.isEmpty()) {
            Map<String, Object> duplicate = duplicates.get(0);
            throw new IllegalStateException(
                    "Duplicate detection records already exist for device_id="
                            + duplicate.get("device_id")
                            + ", pcb_id="
                            + duplicate.get("pcb_id")
            );
        }

        jdbcTemplate.execute("""
                create unique index if not exists uk_detection_device_pcb
                on detection_records (device_id, pcb_id)
                where pcb_id is not null and trim(pcb_id) <> ''
                """);
    }
}
