package com.evops.aqua.sensor.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.sensor.dto.ReviewDecisionRequest;
import com.evops.aqua.sensor.entity.SensorChangeEvidence;
import com.evops.aqua.sensor.entity.SensorReading;
import com.evops.aqua.sensor.entity.SensorReviewItem;
import com.evops.aqua.sensor.mapper.SensorReadingMapper;
import com.evops.aqua.sensor.mapper.SensorReviewItemMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 异常读数复核：确认异常（保持剔除日报）或改判正常（补入日报）。
 * 复核人与复核理由必填；改判正常仅当所属日报区间未审核（OPEN）才补入——
 * 已审核区间的旧结论不被改动。requestId 幂等，重复复核返回首次结果。
 */
@Service
public class SensorReviewService {

    private final SensorReviewItemMapper reviewItemMapper;
    private final SensorReadingMapper readingMapper;
    private final SensorDailyReportService dailyReportService;
    private final SensorEvidenceService evidenceService;

    public SensorReviewService(SensorReviewItemMapper reviewItemMapper,
                               SensorReadingMapper readingMapper,
                               SensorDailyReportService dailyReportService,
                               SensorEvidenceService evidenceService) {
        this.reviewItemMapper = reviewItemMapper;
        this.readingMapper = readingMapper;
        this.dailyReportService = dailyReportService;
        this.evidenceService = evidenceService;
    }

    /**
     * 复核待复核队列项。仅 PENDING 可复核；重复复核（同 requestId 或已复核）返回首次结果。
     */
    @Transactional(rollbackFor = Exception.class)
    public SensorReviewItem review(Long itemId, ReviewDecisionRequest request) {
        SensorReviewItem item = getOrThrow(itemId);
        if (!SensorReviewItem.STATUS_PENDING.equals(item.getStatus())) {
            if (request.getRequestId() != null && request.getRequestId().equals(item.getRequestId())) {
                return item;
            }
            throw new IllegalArgumentException("队列项已复核（" + item.getStatus() + "），不能重复复核");
        }

        SensorReading reading = readingMapper.selectById(item.getReadingId());
        if (reading == null) {
            throw new IllegalArgumentException("待复核读数不存在，id=" + item.getReadingId());
        }
        boolean confirmAbnormal = Boolean.TRUE.equals(request.getConfirmedAbnormal());

        SensorReviewItem itemBefore = copyOf(item);
        SensorReading readingBefore = copyOfReading(reading);

        if (confirmAbnormal) {
            reading.setStatus(SensorReading.STATUS_ABNORMAL_CONFIRMED);
            reading.setIncludedFlag(false);
            item.setStatus(SensorReviewItem.STATUS_CONFIRMED_ABNORMAL);
        } else {
            // 改判正常只能补充未审核区间：已审核区间的旧结论不被改动
            if (dailyReportService.isReviewed(reading.getPondId(), reading.getReportDate())) {
                throw new IllegalArgumentException("读数所属" + reading.getReportDate()
                        + "日报区间已审核，旧结论不能改动，不能改判正常补入；只能确认异常");
            }
            reading.setStatus(SensorReading.STATUS_REVIEWED_NORMAL);
            reading.setIncludedFlag(true);
            item.setStatus(SensorReviewItem.STATUS_REVIEWED_NORMAL);
        }
        readingMapper.updateById(reading);

        item.setReviewedBy(request.getReviewedBy());
        item.setReviewReason(request.getReviewReason());
        item.setReviewedTime(LocalDateTime.now());
        item.setRequestId(request.getRequestId());
        reviewItemMapper.updateById(item);

        evidenceService.record(SensorChangeEvidence.ENTITY_REVIEW, item.getId(),
                SensorChangeEvidence.ACTION_REVIEW, request.getRequestId(), request.getReviewedBy(),
                itemBefore, item,
                confirmAbnormal
                        ? "复核确认异常：" + item.getAbnormalMetrics() + "，保持剔除日报；理由：" + request.getReviewReason()
                        : "复核改判正常：读数重新计入" + reading.getReportDate() + "日报（区间未审核）；理由："
                                + request.getReviewReason());
        evidenceService.record(SensorChangeEvidence.ENTITY_READING, reading.getId(),
                SensorChangeEvidence.ACTION_UPDATE, request.getRequestId(), request.getReviewedBy(),
                readingBefore, reading,
                "复核后读数状态=" + reading.getStatus() + "，纳入日报=" + reading.getIncludedFlag());

        // 改判正常后重算所属日报（区间未审核）；确认异常仅刷新待复核计数
        dailyReportService.recompute(reading.getPondId(), reading.getReportDate(),
                reading.getRuleVersionId(), request.getRequestId(), request.getReviewedBy());
        return item;
    }

    public SensorReviewItem getOrThrow(Long id) {
        SensorReviewItem item = reviewItemMapper.selectById(id);
        if (item == null) {
            throw new IllegalArgumentException("待复核队列项不存在，id=" + id);
        }
        return item;
    }

    public List<SensorReviewItem> list(Long pondId, String status) {
        QueryWrapper<SensorReviewItem> wrapper = new QueryWrapper<>();
        wrapper.eq(pondId != null, "pond_id", pondId);
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.orderByAsc("id");
        return reviewItemMapper.selectList(wrapper);
    }

    private SensorReviewItem copyOf(SensorReviewItem s) {
        SensorReviewItem c = new SensorReviewItem();
        c.setId(s.getId());
        c.setReadingId(s.getReadingId());
        c.setPondId(s.getPondId());
        c.setDeviceId(s.getDeviceId());
        c.setDeviceTime(s.getDeviceTime());
        c.setAbnormalMetrics(s.getAbnormalMetrics());
        c.setReadingSnapshot(s.getReadingSnapshot());
        c.setStatus(s.getStatus());
        c.setReviewedBy(s.getReviewedBy());
        c.setReviewReason(s.getReviewReason());
        c.setReviewedTime(s.getReviewedTime());
        c.setRequestId(s.getRequestId());
        return c;
    }

    private SensorReading copyOfReading(SensorReading s) {
        SensorReading c = new SensorReading();
        c.setId(s.getId());
        c.setPacketId(s.getPacketId());
        c.setDeviceId(s.getDeviceId());
        c.setPondId(s.getPondId());
        c.setDeviceTime(s.getDeviceTime());
        c.setSeqNo(s.getSeqNo());
        c.setSalinityPpt(s.getSalinityPpt());
        c.setDissolvedOxygenMgL(s.getDissolvedOxygenMgL());
        c.setWaterTempC(s.getWaterTempC());
        c.setReportDate(s.getReportDate());
        c.setStatus(s.getStatus());
        c.setIncludedFlag(s.getIncludedFlag());
        c.setAbnormalMetrics(s.getAbnormalMetrics());
        c.setRuleVersionId(s.getRuleVersionId());
        return c;
    }
}
