package com.evops.aqua.sensor.controller;

import com.evops.aqua.sensor.dto.PacketUploadRequest;
import com.evops.aqua.sensor.entity.SensorChangeEvidence;
import com.evops.aqua.sensor.entity.SensorPacket;
import com.evops.aqua.sensor.entity.SensorReading;
import com.evops.aqua.sensor.service.SensorEvidenceService;
import com.evops.aqua.sensor.service.SensorPacketService;
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
import java.util.Map;

/**
 * 设备上传包：离线批量上传盐度/溶氧/温度，服务端按「设备+设备时间+序号」归并；
 * 签名失败整包隔离并保留原文；重复上传幂等不重复计入日报；迟到数据只补未审核区间。
 */
@RestController
@RequestMapping("/api/aqua/sensor")
public class SensorPacketController {

    private final SensorPacketService packetService;
    private final SensorEvidenceService evidenceService;

    public SensorPacketController(SensorPacketService packetService, SensorEvidenceService evidenceService) {
        this.packetService = packetService;
        this.evidenceService = evidenceService;
    }

    /**
     * 接收设备上传包：签名校验失败/原文不可解析整包隔离（保留原文）；
     * 否则逐条归并并重算未审核区间日报；重复上传返回首次归并结果。
     */
    @PostMapping("/packets")
    public ApiResponse<Map<String, Object>> upload(@Validated @RequestBody PacketUploadRequest request) {
        return ApiResponse.ok(packetService.upload(request));
    }

    /** 上传包列表：可按设备与状态（MERGED/QUARANTINED）过滤，隔离包保留原文。 */
    @GetMapping("/packets")
    public ApiResponse<List<SensorPacket>> listPackets(@RequestParam(required = false) Long deviceId,
                                                       @RequestParam(required = false) String status) {
        return ApiResponse.ok(packetService.list(deviceId, status));
    }

    /** 上传包详情：含归并计数与原文。 */
    @GetMapping("/packets/{id}")
    public ApiResponse<SensorPacket> packetDetail(@PathVariable Long id) {
        return ApiResponse.ok(packetService.getOrThrow(id));
    }

    /** 归并读数查询：按池/设备/日期/状态过滤。 */
    @GetMapping("/readings")
    public ApiResponse<List<SensorReading>> listReadings(
            @RequestParam(required = false) Long pondId,
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reportDate,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(packetService.listReadings(pondId, deviceId, reportDate, status));
    }

    /** 变更证据查询：可按对象、动作或幂等请求号追溯处理人。 */
    @GetMapping("/evidence")
    public ApiResponse<List<SensorChangeEvidence>> evidence(@RequestParam(required = false) String entityType,
                                                            @RequestParam(required = false) Long entityId,
                                                            @RequestParam(required = false) String requestId,
                                                            @RequestParam(required = false) String action) {
        return ApiResponse.ok(evidenceService.list(entityType, entityId, requestId, action));
    }
}
