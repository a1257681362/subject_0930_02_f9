package com.evops.aqua.sensor.controller;

import com.evops.aqua.sensor.dto.DeviceRegisterRequest;
import com.evops.aqua.sensor.dto.DeviceReplaceRequest;
import com.evops.aqua.sensor.entity.SensorDevice;
import com.evops.aqua.sensor.service.SensorDeviceService;
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
import java.util.Map;

/**
 * 水质传感器设备：登记（幂等）、更换（建立新旧关联，旧设备置 REPLACED）、新旧关联链追溯。
 */
@RestController
@RequestMapping("/api/aqua/sensor/devices")
public class SensorDeviceController {

    private final SensorDeviceService deviceService;

    public SensorDeviceController(SensorDeviceService deviceService) {
        this.deviceService = deviceService;
    }

    /** 登记设备：按池塘安装并下发签名密钥；requestId 幂等。 */
    @PostMapping
    public ApiResponse<SensorDevice> register(@Validated @RequestBody DeviceRegisterRequest request) {
        return ApiResponse.ok(deviceService.register(request));
    }

    /** 设备更换：新设备 predecessor 指向旧设备，旧设备置 REPLACED；更换原因必填，幂等。 */
    @PostMapping("/{id}/replace")
    public ApiResponse<SensorDevice> replace(@PathVariable Long id,
                                             @Validated @RequestBody DeviceReplaceRequest request) {
        return ApiResponse.ok(deviceService.replace(id, request));
    }

    @GetMapping
    public ApiResponse<List<SensorDevice>> list(@RequestParam(required = false) Long pondId,
                                                @RequestParam(required = false) String status) {
        return ApiResponse.ok(deviceService.list(pondId, status));
    }

    /** 新旧关联链：沿 predecessor/replacedBy 串起同池历任设备，数据序列跨设备延续。 */
    @GetMapping("/{id}/lineage")
    public ApiResponse<Map<String, Object>> lineage(@PathVariable Long id) {
        return ApiResponse.ok(deviceService.lineage(id));
    }
}
