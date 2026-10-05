package com.evops.aqua.sensor.controller;

import com.evops.aqua.sensor.dto.SensorRulePublishRequest;
import com.evops.aqua.sensor.entity.SensorRuleVersion;
import com.evops.aqua.sensor.service.SensorRuleVersionService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 归并规则版本：规则调整只能发布新版本（旧版本自动停用，无修改/删除入口）；
 * 已归并的包/读数/日报冻结当时版本，离线归并旧结论不随新版本回改。
 */
@RestController
@RequestMapping("/api/aqua/sensor/rule-versions")
public class SensorRuleController {

    private final SensorRuleVersionService ruleVersionService;

    public SensorRuleController(SensorRuleVersionService ruleVersionService) {
        this.ruleVersionService = ruleVersionService;
    }

    /** 发布规则新版本：旧 ACTIVE 版本自动置 SUPERSEDED；requestId 幂等。 */
    @PostMapping
    public ApiResponse<SensorRuleVersion> publish(@Validated @RequestBody SensorRulePublishRequest request) {
        return ApiResponse.ok(ruleVersionService.publish(request));
    }

    @GetMapping
    public ApiResponse<List<SensorRuleVersion>> list(@RequestParam(required = false) String status) {
        return ApiResponse.ok(ruleVersionService.list(status));
    }

    /** 当前生效规则版本：归并上传包时冻结该版本指针。 */
    @GetMapping("/active")
    public ApiResponse<SensorRuleVersion> active() {
        return ApiResponse.ok(ruleVersionService.currentActive());
    }
}
