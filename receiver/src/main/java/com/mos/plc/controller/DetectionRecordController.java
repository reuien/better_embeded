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

    @GetMapping("/api/detection-records")
    public ApiResponse<PageResponse<DetectionRecordResponse>> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String defectType,
            @RequestParam(required = false) String result,
            @PageableDefault(sort = "detectTime", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.success(PageResponse.from(service.search(startTime, endTime, deviceId, defectType, result, pageable)));
    }

    @GetMapping("/api/detection-records/{id}")
    public ApiResponse<DetectionRecordResponse> get(@PathVariable Long id) {
        return ApiResponse.success(service.get(id));
    }

    @DeleteMapping("/api/detection-records/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success();
    }
}
