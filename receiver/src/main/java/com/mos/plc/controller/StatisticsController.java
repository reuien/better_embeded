package com.mos.plc.controller;

import com.mos.plc.dto.ApiResponse;
import com.mos.plc.dto.StatisticsResponse;
import com.mos.plc.service.StatisticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatisticsController {
    private final StatisticsService service;

    public StatisticsController(StatisticsService service) {
        this.service = service;
    }

    @GetMapping("/api/statistics")
    public ApiResponse<StatisticsResponse> statistics() {
        return ApiResponse.success(service.current());
    }
}
