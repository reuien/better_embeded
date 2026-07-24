package com.mos.plc.controller;

import com.mos.plc.dto.ApiResponse;
import com.mos.plc.dto.DetectionRecordResponse;
import com.mos.plc.dto.PageResponse;
import com.mos.plc.service.DetectionRecordService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;

@RestController
public class DetectionRecordController {
    private final DetectionRecordService service;

    public DetectionRecordController(DetectionRecordService service) {
        this.service = service;
    }

    @PostMapping("/api/detection-records")
    public ApiResponse<DetectionRecordResponse> upload(
            @RequestPart("image") MultipartFile image,
            @RequestParam String deviceId,
            @RequestParam String result,
            @RequestParam(required = false) String defectType,
            @RequestParam(required = false) Double confidence,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime detectTime
    ) throws IOException {
        return ApiResponse.success(service.create(image, deviceId, result, defectType, confidence, detectTime));
    }

    @PostMapping("/api/detection/upload")
    public ApiResponse<DetectionRecordResponse> uploadBoardDetection(
            @RequestPart("original_image") MultipartFile originalImage,
            @RequestPart("result_image") MultipartFile resultImage,
            @RequestParam("device_id") String deviceId,
            @RequestParam("pcb_id") String pcbId,
            @RequestParam(value = "capture_time", required = false) String captureTime,
            @RequestParam String result,
            @RequestParam(value = "inference_time_ms", required = false) Integer inferenceTimeMs,
            @RequestParam(required = false) String defects
    ) throws IOException {
        return ApiResponse.success(service.createBoardUpload(
                originalImage,
                resultImage,
                deviceId,
                pcbId,
                captureTime,
                result,
                inferenceTimeMs,
                defects
        ));
    }

    @GetMapping({"/api/detection-records", "/api/detection/list"})
    public ApiResponse<PageResponse<DetectionRecordResponse>> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String pcbId,
            @RequestParam(value = "pcb_id", required = false) String pcbIdSnake,
            @RequestParam(required = false) String defectType,
            @RequestParam(required = false) String result,
            @PageableDefault(sort = "detectTime", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.success(PageResponse.from(service.search(
                startTime,
                endTime,
                deviceId,
                firstNonBlank(pcbId, pcbIdSnake),
                defectType,
                result,
                pageable
        )));
    }

    @GetMapping("/api/detection/latest")
    public ApiResponse<DetectionRecordResponse> latest() {
        return ApiResponse.success(service.latest());
    }

    @GetMapping({"/api/detection-records/{id}", "/api/detection/{id}"})
    public ApiResponse<DetectionRecordResponse> get(@PathVariable Long id) {
        return ApiResponse.success(service.get(id));
    }

    @DeleteMapping("/api/detection-records/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
