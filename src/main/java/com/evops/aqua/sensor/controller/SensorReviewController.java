package com.evops.aqua.sensor.controller;

import com.evops.aqua.sensor.dto.ReviewDecisionRequest;
import com.evops.aqua.sensor.entity.SensorReviewItem;
import com.evops.aqua.sensor.service.SensorReviewService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 异常读数待复核队列：异常读数复核前不计入日报；
 * 复核须登记复核人与理由，改判正常仅当所属日报区间未审核才补入。
 */
@RestController
@RequestMapping("/api/aqua/sensor/review-queue")
public class SensorReviewController {

    private final SensorReviewService reviewService;

    public SensorReviewController(SensorReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public ApiResponse<List<SensorReviewItem>> list(@RequestParam(required = false) Long pondId,
                                                    @RequestParam(required = false) String status) {
        return ApiResponse.ok(reviewService.list(pondId, status));
    }

    /** 复核：确认异常保持剔除；改判正常补入未审核区间日报；复核人与理由必填，幂等。 */
    @PostMapping("/{id}/review")
    public ApiResponse<SensorReviewItem> review(@PathVariable Long id,
                                                @Validated @RequestBody ReviewDecisionRequest request) {
        return ApiResponse.ok(reviewService.review(id, request));
    }
}
