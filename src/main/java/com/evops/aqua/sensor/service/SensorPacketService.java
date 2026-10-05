package com.evops.aqua.sensor.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.sensor.dto.PacketUploadRequest;
import com.evops.aqua.sensor.entity.SensorChangeEvidence;
import com.evops.aqua.sensor.entity.SensorDevice;
import com.evops.aqua.sensor.entity.SensorPacket;
import com.evops.aqua.sensor.entity.SensorReading;
import com.evops.aqua.sensor.entity.SensorReviewItem;
import com.evops.aqua.sensor.entity.SensorRuleVersion;
import com.evops.aqua.sensor.mapper.SensorPacketMapper;
import com.evops.aqua.sensor.mapper.SensorReadingMapper;
import com.evops.aqua.sensor.mapper.SensorReviewItemMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 设备上传包归并：设备离线后批量上传盐度/溶氧/温度。
 * 1) 签名校验：HMAC-SHA256(原文, 设备密钥) 不符 → 整包隔离（QUARANTINED）并保留原文，不归并；
 * 2) 幂等：requestId 与（设备+包号）双幂等，重复上传返回首次归并结果，不重复计入日报；
 * 3) 归并键「设备 + 设备时间 + 序号」：重复读数识别为 DUPLICATE 不产生第二行；
 * 4) 迟到数据只能补充未审核区间：所属日报已 REVIEWED 的读数标记 LATE_REJECTED 留存不计入；
 * 5) 异常读数（超当时生效规则版本阈值）进待复核队列，复核前不计入日报；
 * 6) 归并冻结当时规则版本指针，规则新版本不回改旧结论。
 */
@Service
public class SensorPacketService {

    private final SensorPacketMapper packetMapper;
    private final SensorReadingMapper readingMapper;
    private final SensorReviewItemMapper reviewItemMapper;
    private final SensorDeviceService deviceService;
    private final SensorRuleVersionService ruleVersionService;
    private final SensorDailyReportService dailyReportService;
    private final SensorEvidenceService evidenceService;
    private final ObjectMapper objectMapper;

    public SensorPacketService(SensorPacketMapper packetMapper,
                               SensorReadingMapper readingMapper,
                               SensorReviewItemMapper reviewItemMapper,
                               SensorDeviceService deviceService,
                               SensorRuleVersionService ruleVersionService,
                               SensorDailyReportService dailyReportService,
                               SensorEvidenceService evidenceService,
                               ObjectMapper objectMapper) {
        this.packetMapper = packetMapper;
        this.readingMapper = readingMapper;
        this.reviewItemMapper = reviewItemMapper;
        this.deviceService = deviceService;
        this.ruleVersionService = ruleVersionService;
        this.dailyReportService = dailyReportService;
        this.evidenceService = evidenceService;
        this.objectMapper = objectMapper;
    }

    /**
     * 接收设备上传包并归并。签名失败/原文不可解析/包文不一致 → 隔离留原文；
     * 否则按归并键逐条归并并重算未审核区间日报。重复上传返回首次结果。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> upload(PacketUploadRequest request) {
        SensorDevice device = deviceService.getByCodeOrThrow(request.getDeviceCode());

        // 幂等：requestId 或（设备+包号）命中即返回首次归并结果，不重复归并
        SensorPacket existed = findByRequestId(request.getRequestId());
        if (existed == null) {
            existed = findByDeviceAndPacketNo(device.getId(), request.getPacketNo());
        }
        if (existed != null) {
            return resultOf(existed, readingsOf(existed.getId()), true);
        }

        // 签名校验：失败整包隔离并保留原文，不做任何归并
        if (!SensorSignatureUtil.matches(request.getRawPayload(), device.getSecretKey(), request.getSignature())) {
            SensorPacket packet = quarantine(device, request, false, "签名校验失败：设备" + device.getDeviceCode()
                    + "上传包" + request.getPacketNo() + "签名与原文/密钥不符，整包隔离保留原文");
            return resultOf(packet, new ArrayList<SensorReading>(), false);
        }

        // 解析原文：不可解析或包文与上传设备/包号不一致同样隔离留原文
        JsonNode payload;
        try {
            payload = objectMapper.readTree(request.getRawPayload());
        } catch (Exception ex) {
            SensorPacket packet = quarantine(device, request, true,
                    "原始报文无法解析为JSON，整包隔离保留原文：" + ex.getMessage());
            return resultOf(packet, new ArrayList<SensorReading>(), false);
        }
        String payloadDevice = textOf(payload, "deviceCode");
        String payloadPacketNo = textOf(payload, "packetNo");
        if (!device.getDeviceCode().equals(payloadDevice) || !request.getPacketNo().equals(payloadPacketNo)) {
            SensorPacket packet = quarantine(device, request, true,
                    "报文内设备/包号（" + payloadDevice + "/" + payloadPacketNo + "）与上传设备/包号（"
                            + device.getDeviceCode() + "/" + request.getPacketNo() + "）不一致，整包隔离保留原文");
            return resultOf(packet, new ArrayList<SensorReading>(), false);
        }
        JsonNode readingsNode = payload.get("readings");
        if (readingsNode == null || !readingsNode.isArray()) {
            SensorPacket packet = quarantine(device, request, true,
                    "原始报文缺少readings数组，整包隔离保留原文");
            return resultOf(packet, new ArrayList<SensorReading>(), false);
        }

        // 冻结归并当时生效规则版本：规则新版本不回改本包结论
        SensorRuleVersion rule = ruleVersionService.currentActive();

        SensorPacket packet = new SensorPacket();
        packet.setPacketNo(request.getPacketNo());
        packet.setDeviceId(device.getId());
        packet.setRequestId(request.getRequestId());
        packet.setSignature(request.getSignature());
        packet.setSignatureOk(true);
        packet.setStatus(SensorPacket.STATUS_MERGED);
        packet.setRawPayload(request.getRawPayload());
        packet.setRuleVersionId(rule.getId());
        packet.setReceivedBy(request.getReceivedBy());
        packet.setTotalCount(readingsNode.size());
        packet.setMergedCount(0);
        packet.setDuplicateCount(0);
        packet.setLateRejectedCount(0);
        packet.setAbnormalCount(0);
        packetMapper.insert(packet);

        List<SensorReading> stored = new ArrayList<>();
        Set<LocalDate> affectedDates = new LinkedHashSet<>();
        int merged = 0, duplicated = 0, lateRejected = 0, abnormal = 0, ordinal = 0;
        for (JsonNode node : readingsNode) {
            ordinal++;
            SensorReading reading;
            try {
                reading = toReading(node, packet, device, rule);
            } catch (Exception ex) {
                throw new IllegalArgumentException("包" + request.getPacketNo() + "第"
                        + ordinal + "条读数解析失败：" + ex.getMessage());
            }

            SensorReading duplicate = findReading(device.getId(), reading.getDeviceTime(), reading.getSeqNo());
            if (duplicate != null) {
                duplicated++;
                stored.add(duplicate);
                continue;
            }

            if (dailyReportService.isReviewed(device.getPondId(), reading.getReportDate())) {
                // 迟到数据只能补充未审核区间：已审核区间的迟到读数留存但不计入
                reading.setStatus(SensorReading.STATUS_LATE_REJECTED);
                reading.setIncludedFlag(false);
                readingMapper.insert(reading);
                lateRejected++;
                stored.add(reading);
                evidenceService.record(SensorChangeEvidence.ENTITY_READING, reading.getId(),
                        SensorChangeEvidence.ACTION_LATE_REJECT, request.getRequestId(),
                        request.getReceivedBy(), null, reading,
                        "迟到读数（设备时间=" + reading.getDeviceTime() + "，序号=" + reading.getSeqNo()
                                + "）：池塘id=" + device.getPondId() + "的" + reading.getReportDate()
                                + "日报已审核，迟到数据只能补充未审核区间，本条留存不计入");
                continue;
            }

            String abnormalMetrics = abnormalMetricsOf(reading, rule);
            if (abnormalMetrics != null) {
                // 异常读数进待复核队列，复核前不计入日报
                reading.setStatus(SensorReading.STATUS_ABNORMAL_PENDING);
                reading.setIncludedFlag(false);
                reading.setAbnormalMetrics(abnormalMetrics);
                readingMapper.insert(reading);
                abnormal++;
                stored.add(reading);
                SensorReviewItem item = new SensorReviewItem();
                item.setReadingId(reading.getId());
                item.setPondId(reading.getPondId());
                item.setDeviceId(reading.getDeviceId());
                item.setDeviceTime(reading.getDeviceTime());
                item.setAbnormalMetrics(abnormalMetrics);
                item.setReadingSnapshot(snapshotOf(reading));
                item.setStatus(SensorReviewItem.STATUS_PENDING);
                reviewItemMapper.insert(item);
                evidenceService.record(SensorChangeEvidence.ENTITY_REVIEW, item.getId(),
                        SensorChangeEvidence.ACTION_FLAG, request.getRequestId(),
                        request.getReceivedBy(), null, item,
                        "异常读数进待复核队列：指标[" + abnormalMetrics + "]超出规则版本"
                                + rule.getVersionNo() + "范围，复核前不计入日报");
            } else {
                reading.setStatus(SensorReading.STATUS_NORMAL);
                reading.setIncludedFlag(true);
                readingMapper.insert(reading);
                merged++;
                stored.add(reading);
            }
            affectedDates.add(reading.getReportDate());
        }

        // 重算受影响日期的日报（仅未审核区间；重复读数未落行，天然不重复计入）
        for (LocalDate date : affectedDates) {
            dailyReportService.recompute(device.getPondId(), date, rule.getId(),
                    request.getRequestId(), request.getReceivedBy());
        }

        packet.setMergedCount(merged);
        packet.setDuplicateCount(duplicated);
        packet.setLateRejectedCount(lateRejected);
        packet.setAbnormalCount(abnormal);
        packetMapper.updateById(packet);
        evidenceService.record(SensorChangeEvidence.ENTITY_PACKET, packet.getId(),
                SensorChangeEvidence.ACTION_MERGE, request.getRequestId(), request.getReceivedBy(),
                null, packet,
                "设备" + device.getDeviceCode() + "上传包" + packet.getPacketNo() + "归并完成：共"
                        + packet.getTotalCount() + "条，新归并" + merged + "条，重复" + duplicated
                        + "条（不重复计入日报），迟到拒收" + lateRejected + "条，异常待复核" + abnormal
                        + "条；冻结规则版本" + rule.getVersionNo());
        return resultOf(packet, stored, false);
    }

    public SensorPacket getOrThrow(Long id) {
        SensorPacket packet = packetMapper.selectById(id);
        if (packet == null) {
            throw new IllegalArgumentException("上传包不存在，id=" + id);
        }
        return packet;
    }

    public List<SensorPacket> list(Long deviceId, String status) {
        QueryWrapper<SensorPacket> wrapper = new QueryWrapper<>();
        wrapper.eq(deviceId != null, "device_id", deviceId);
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.orderByAsc("id");
        return packetMapper.selectList(wrapper);
    }

    public List<SensorReading> readingsOf(Long packetId) {
        return readingMapper.selectList(new QueryWrapper<SensorReading>()
                .eq("packet_id", packetId).orderByAsc("seq_no"));
    }

    public List<SensorReading> listReadings(Long pondId, Long deviceId, LocalDate reportDate, String status) {
        QueryWrapper<SensorReading> wrapper = new QueryWrapper<>();
        wrapper.eq(pondId != null, "pond_id", pondId);
        wrapper.eq(deviceId != null, "device_id", deviceId);
        wrapper.eq(reportDate != null, "report_date", reportDate);
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.orderByAsc("device_time", "seq_no");
        return readingMapper.selectList(wrapper);
    }

    // ------------------------------------------------------------
    // 内部辅助
    // ------------------------------------------------------------

    private SensorPacket quarantine(SensorDevice device, PacketUploadRequest request,
                                    boolean signatureOk, String reason) {
        SensorPacket packet = new SensorPacket();
        packet.setPacketNo(request.getPacketNo());
        packet.setDeviceId(device.getId());
        packet.setRequestId(request.getRequestId());
        packet.setSignature(request.getSignature());
        packet.setSignatureOk(signatureOk);
        packet.setStatus(SensorPacket.STATUS_QUARANTINED);
        packet.setRawPayload(request.getRawPayload());
        packet.setReceivedBy(request.getReceivedBy());
        packet.setQuarantineReason(reason);
        packet.setTotalCount(0);
        packet.setMergedCount(0);
        packet.setDuplicateCount(0);
        packet.setLateRejectedCount(0);
        packet.setAbnormalCount(0);
        packetMapper.insert(packet);
        evidenceService.record(SensorChangeEvidence.ENTITY_PACKET, packet.getId(),
                SensorChangeEvidence.ACTION_QUARANTINE, request.getRequestId(),
                request.getReceivedBy(), null, packet,
                reason + "；原文已留存于raw_payload，未做任何归并");
        return packet;
    }

    private SensorReading toReading(JsonNode node, SensorPacket packet, SensorDevice device,
                                    SensorRuleVersion rule) {
        SensorReading reading = new SensorReading();
        reading.setPacketId(packet.getId());
        reading.setDeviceId(device.getId());
        reading.setPondId(device.getPondId());
        reading.setSeqNo(requiredInt(node, "seqNo"));
        reading.setDeviceTime(LocalDateTime.parse(requiredText(node, "deviceTime")));
        reading.setSalinityPpt(requiredDecimal(node, "salinityPpt"));
        reading.setDissolvedOxygenMgL(requiredDecimal(node, "dissolvedOxygenMgL"));
        reading.setWaterTempC(requiredDecimal(node, "waterTempC"));
        reading.setReportDate(reading.getDeviceTime().toLocalDate());
        reading.setRuleVersionId(rule.getId());
        return reading;
    }

    /** 超出规则版本闭区间的指标列表；全部正常返回 null。 */
    private String abnormalMetricsOf(SensorReading reading, SensorRuleVersion rule) {
        StringBuilder metrics = new StringBuilder();
        if (reading.getSalinityPpt().compareTo(rule.getSalinityMin()) < 0
                || reading.getSalinityPpt().compareTo(rule.getSalinityMax()) > 0) {
            metrics.append("SALINITY");
        }
        if (reading.getDissolvedOxygenMgL().compareTo(rule.getDoMin()) < 0
                || reading.getDissolvedOxygenMgL().compareTo(rule.getDoMax()) > 0) {
            if (metrics.length() > 0) {
                metrics.append(",");
            }
            metrics.append("DO");
        }
        if (reading.getWaterTempC().compareTo(rule.getTempMin()) < 0
                || reading.getWaterTempC().compareTo(rule.getTempMax()) > 0) {
            if (metrics.length() > 0) {
                metrics.append(",");
            }
            metrics.append("TEMP");
        }
        return metrics.length() == 0 ? null : metrics.toString();
    }

    private String snapshotOf(SensorReading reading) {
        return "盐度=" + reading.getSalinityPpt() + "ppt,溶氧=" + reading.getDissolvedOxygenMgL()
                + "mg/L,温度=" + reading.getWaterTempC() + "℃";
    }

    private SensorReading findReading(Long deviceId, LocalDateTime deviceTime, Integer seqNo) {
        List<SensorReading> rows = readingMapper.selectList(new QueryWrapper<SensorReading>()
                .eq("device_id", deviceId).eq("device_time", deviceTime).eq("seq_no", seqNo));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private SensorPacket findByRequestId(String requestId) {
        if (requestId == null || requestId.trim().isEmpty()) {
            return null;
        }
        List<SensorPacket> rows = packetMapper.selectList(
                new QueryWrapper<SensorPacket>().eq("request_id", requestId));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private SensorPacket findByDeviceAndPacketNo(Long deviceId, String packetNo) {
        List<SensorPacket> rows = packetMapper.selectList(new QueryWrapper<SensorPacket>()
                .eq("device_id", deviceId).eq("packet_no", packetNo));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> resultOf(SensorPacket packet, List<SensorReading> readings, boolean idempotentReplay) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("packet", packet);
        result.put("readings", readings);
        result.put("idempotentReplay", idempotentReplay);
        return result;
    }

    private String textOf(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String requiredText(JsonNode node, String field) {
        String value = textOf(node, field);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("读数缺少字段" + field);
        }
        return value.trim();
    }

    private Integer requiredInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException("读数缺少数值字段" + field);
        }
        return value.asInt();
    }

    private BigDecimal requiredDecimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException("读数缺少数值字段" + field);
        }
        return value.decimalValue();
    }
}
