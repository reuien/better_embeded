package com.mos.plc;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ReceiverApplicationTests {
    private static final byte[] PNG_1X1 = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII="
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${plc.upload-root}")
    private String uploadRoot;

    @Test
    void contextLoads() {
    }

    @Test
    void createsBoardIdentityUniqueIndex() {
        List<Map<String, Object>> indexes = jdbcTemplate.queryForList("pragma index_list('detection_records')");

        assertThat(indexes)
                .anySatisfy(index -> {
                    assertThat(index.get("name")).isEqualTo("uk_detection_device_pcb");
                    assertThat(String.valueOf(index.get("unique"))).isEqualTo("1");
                });
    }

    @Test
    void acceptsBoardUpload() throws Exception {
        mockMvc.perform(multipart("/api/detection/upload")
                        .file(imagePart("original_image"))
                        .file(imagePart("result_image"))
                        .param("device_id", "BOARD-01")
                        .param("pcb_id", "PCB-ACCEPT-001")
                        .param("capture_time", "2026-07-23 11:30:00")
                        .param("result", "NG")
                        .param("inference_time_ms", "42")
                        .param("defects", "[{\"class_name\":\"missing\",\"confidence\":0.86,\"bbox\":[1,2,3,4]}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.deviceId").value("BOARD-01"))
                .andExpect(jsonPath("$.data.pcbId").value("PCB-ACCEPT-001"))
                .andExpect(jsonPath("$.data.result").value("defect"))
                .andExpect(jsonPath("$.data.defectType").value("missing"))
                .andExpect(jsonPath("$.data.confidence").value(0.86))
                .andExpect(jsonPath("$.data.defectCount").value(1))
                .andExpect(jsonPath("$.data.inferenceTimeMs").value(42))
                .andExpect(jsonPath("$.data.imageUrl").exists())
                .andExpect(jsonPath("$.data.originalImageUrl").exists())
                .andExpect(jsonPath("$.data.resultImageUrl").exists())
                .andExpect(jsonPath("$.data.defects[0].x1").value(1.0));
    }

    @Test
    void uploadPersistsFilesAndDatabaseRecordAndReadApisReturnIt() throws Exception {
        String deviceId = "BOARD-READ-01";
        String pcbId = "PCB-READ-001";

        mockMvc.perform(multipart("/api/detection/upload")
                        .file(imagePart("original_image"))
                        .file(imagePart("result_image"))
                        .param("device_id", deviceId)
                        .param("pcb_id", pcbId)
                        .param("capture_time", "2026-07-23 23:59:59")
                        .param("result", "NG")
                        .param("inference_time_ms", "42")
                        .param("defects", "[{\"class_name\":\"missing\",\"confidence\":0.86,\"bbox\":[1,2,3,4]}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deviceId").value(deviceId))
                .andExpect(jsonPath("$.data.pcbId").value(pcbId));

        Map<String, Object> persisted = jdbcTemplate.queryForMap(
                "select id, original_image_path, result_image_path from detection_records where device_id = ? and pcb_id = ?",
                deviceId,
                pcbId
        );
        Long recordId = ((Number) persisted.get("id")).longValue();
        assertThat(Path.of(String.valueOf(persisted.get("original_image_path")))).exists().isRegularFile();
        assertThat(Path.of(String.valueOf(persisted.get("result_image_path")))).exists().isRegularFile();

        mockMvc.perform(get("/api/detection/list").param("pcb_id", pcbId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(recordId))
                .andExpect(jsonPath("$.data.content[0].deviceId").value(deviceId))
                .andExpect(jsonPath("$.data.content[0].pcbId").value(pcbId));

        mockMvc.perform(get("/api/detection/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(recordId))
                .andExpect(jsonPath("$.data.pcbId").value(pcbId));

        mockMvc.perform(get("/api/detection/{id}", recordId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(recordId))
                .andExpect(jsonPath("$.data.deviceId").value(deviceId))
                .andExpect(jsonPath("$.data.pcbId").value(pcbId));
    }

    @Test
    void rejectsDuplicateBoardUpload() throws Exception {
        performValidUpload("BOARD-01", "PCB-DUP-001")
                .andExpect(status().isOk());

        performValidUpload("BOARD-01", "PCB-DUP-001")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void rejectsUnsupportedResultValue() throws Exception {
        mockMvc.perform(multipart("/api/detection/upload")
                        .file(imagePart("original_image"))
                        .file(imagePart("result_image"))
                        .param("device_id", "BOARD-01")
                        .param("pcb_id", "PCB-BAD-RESULT-001")
                        .param("result", "BROKEN")
                        .param("defects", "[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void rejectsDefectResultWithoutDefects() throws Exception {
        mockMvc.perform(multipart("/api/detection/upload")
                        .file(imagePart("original_image"))
                        .file(imagePart("result_image"))
                        .param("device_id", "BOARD-01")
                        .param("pcb_id", "PCB-NO-DEFECTS-001")
                        .param("result", "NG")
                        .param("defects", "[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void rejectsUnreadableImageFile() throws Exception {
        MockMultipartFile fakeImage = new MockMultipartFile(
                "original_image",
                "original.jpg",
                "image/jpeg",
                "not an image".getBytes()
        );

        mockMvc.perform(multipart("/api/detection/upload")
                        .file(fakeImage)
                        .file(imagePart("result_image"))
                        .param("device_id", "BOARD-01")
                        .param("pcb_id", "PCB-BAD-IMAGE-001")
                        .param("result", "OK"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void rejectsUnreadableResultImageWithoutKeepingOriginalFile() throws Exception {
        long before = countUploadFiles();
        MockMultipartFile fakeResultImage = new MockMultipartFile(
                "result_image",
                "result.jpg",
                "image/jpeg",
                "not an image".getBytes()
        );

        mockMvc.perform(multipart("/api/detection/upload")
                        .file(imagePart("original_image"))
                        .file(fakeResultImage)
                        .param("device_id", "BOARD-01")
                        .param("pcb_id", "PCB-BAD-RESULT-IMAGE-001")
                        .param("result", "OK"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        assertThat(countUploadFiles()).isEqualTo(before);
    }

    private org.springframework.test.web.servlet.ResultActions performValidUpload(String deviceId, String pcbId) throws Exception {
        return mockMvc.perform(multipart("/api/detection/upload")
                .file(imagePart("original_image"))
                .file(imagePart("result_image"))
                .param("device_id", deviceId)
                .param("pcb_id", pcbId)
                .param("capture_time", "2026-07-23 11:30:00")
                .param("result", "NG")
                .param("inference_time_ms", "42")
                .param("defects", "[{\"class_name\":\"missing\",\"confidence\":0.86,\"bbox\":[1,2,3,4]}]"));
    }

    private static MockMultipartFile imagePart(String name) {
        return new MockMultipartFile(name, name + ".png", "image/png", PNG_1X1);
    }

    private long countUploadFiles() throws IOException {
        Path root = Path.of(uploadRoot);
        if (!Files.exists(root)) {
            return 0;
        }
        try (var stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile).count();
        }
    }
}
