package com.evops.aqua.sensor.controller;

import com.evops.aqua.sensor.dto.DailyReportReviewRequest;
import com.evops.aqua.sensor.entity.SensorDailyReport;
import com.evops.aqua.sensor.service.SensorDailyReportService;
import com.evops.common.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 水质日报：按池按日汇总已纳入读数；未审核（OPEN）区间可被归并/复核补充，
 * 审核（REVIEWED）后冻结——迟到数据只能拒收留存，不再改动本报结论。
 */
@RestController
@RequestMapping("/api/aqua/sensor/daily-reports")
public class SensorDailyReportController {

    private final SensorDailyReportService dailyReportService;

    public SensorDailyReportController(SensorDailyReportService dailyReportService) {
        this.dailyReportService = dailyReportService;
    }

    @GetMapping
    public ApiResponse<List<SensorDailyReport>> list(
            @RequestParam(required = false) Long pondId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reportDate,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(dailyReportService.list(pondId, reportDate, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<SensorDailyReport> detail(@PathVariable Long id) {
        return ApiResponse.ok(dailyReportService.getOrThrow(id));
    }

    /** 日报审核：审核人必填，通过后区间冻结；重复审核幂等返回原报。 */
    @PostMapping("/{id}/review")
    public ApiResponse<SensorDailyReport> review(@PathVariable Long id,
                                                 @Validated @RequestBody DailyReportReviewRequest request) {
        return ApiResponse.ok(dailyReportService.review(id, request));
    }
}
