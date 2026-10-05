package com.evops.aqua.sensor.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.sensor.dto.DailyReportReviewRequest;
import com.evops.aqua.sensor.entity.SensorChangeEvidence;
import com.evops.aqua.sensor.entity.SensorDailyReport;
import com.evops.aqua.sensor.entity.SensorReading;
import com.evops.aqua.sensor.mapper.SensorDailyReportMapper;
import com.evops.aqua.sensor.mapper.SensorReadingMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 水质日报：按池按日汇总已纳入读数（included_flag=true：NORMAL + REVIEWED_NORMAL）。
 * OPEN（未审核）时可被归并/复核重算；REVIEWED（已审核）后冻结——
 * 迟到数据一律 LATE_REJECTED 不再改动本报，规则新版本也不回改旧结论。
 * 重复上传靠读数唯一键（设备+设备时间+序号）去重，日报由读数行重算，天然不重复计入。
 */
@Service
public class SensorDailyReportService {

    private static final int AVG_SCALE = 3;

    private final SensorDailyReportMapper reportMapper;
    private final SensorReadingMapper readingMapper;
    private final SensorEvidenceService evidenceService;

    public SensorDailyReportService(SensorDailyReportMapper reportMapper,
                                    SensorReadingMapper readingMapper,
                                    SensorEvidenceService evidenceService) {
        this.reportMapper = reportMapper;
        this.readingMapper = readingMapper;
        this.evidenceService = evidenceService;
    }

    /**
     * 指定池塘指定日的日报是否已审核（已审核区间的迟到数据只能拒收，不能补充）。
     */
    public boolean isReviewed(Long pondId, LocalDate reportDate) {
        SensorDailyReport report = findNullable(pondId, reportDate);
        return report != null && SensorDailyReport.STATUS_REVIEWED.equals(report.getStatus());
    }

    /**
     * 重算某池某日日报：仅当日报不存在（新建 OPEN）或仍为 OPEN 时重算；
     * 已 REVIEWED 的日报一律不动（迟到数据不能补充已审核区间，旧结论不改）。
     */
    @Transactional(rollbackFor = Exception.class)
    public SensorDailyReport recompute(Long pondId, LocalDate reportDate, Long ruleVersionId,
                                       String requestId, String operator) {
        SensorDailyReport report = findNullable(pondId, reportDate);
        if (report != null && SensorDailyReport.STATUS_REVIEWED.equals(report.getStatus())) {
            return report;
        }

        List<SensorReading> included = readingMapper.selectList(new QueryWrapper<SensorReading>()
                .eq("pond_id", pondId)
                .eq("report_date", reportDate)
                .eq("included_flag", true));
        Long pending = readingMapper.selectCount(new QueryWrapper<SensorReading>()
                .eq("pond_id", pondId)
                .eq("report_date", reportDate)
                .eq("status", SensorReading.STATUS_ABNORMAL_PENDING));

        boolean isNew = report == null;
        SensorDailyReport before = isNew ? null : copyOf(report);
        if (isNew) {
            report = new SensorDailyReport();
            report.setPondId(pondId);
            report.setReportDate(reportDate);
            report.setStatus(SensorDailyReport.STATUS_OPEN);
        }
        report.setReadingCount(included.size());
        report.setAbnormalPendingCount(pending == null ? 0 : pending.intValue());
        applyMetricStats(report, included);
        report.setRuleVersionId(ruleVersionId);

        if (isNew) {
            reportMapper.insert(report);
            evidenceService.record(SensorChangeEvidence.ENTITY_DAILY_REPORT, report.getId(),
                    SensorChangeEvidence.ACTION_CREATE, requestId, operator, null, report,
                    "生成" + reportDate + "水质日报（OPEN，未审核区间可继续补充）：纳入读数="
                            + included.size() + "条，待复核异常=" + report.getAbnormalPendingCount() + "条");
        } else {
            reportMapper.updateById(report);
            evidenceService.record(SensorChangeEvidence.ENTITY_DAILY_REPORT, report.getId(),
                    SensorChangeEvidence.ACTION_UPDATE, requestId, operator, before, report,
                    "重算" + reportDate + "水质日报（区间未审核）：纳入读数=" + included.size()
                            + "条，待复核异常=" + report.getAbnormalPendingCount() + "条");
        }
        return report;
    }

    /**
     * 日报审核：审核人必填，通过后区间冻结（REVIEWED）。
     * 冻结后迟到数据只能 LATE_REJECTED 留存，不再改动本报；重复审核幂等返回原报。
     */
    @Transactional(rollbackFor = Exception.class)
    public SensorDailyReport review(Long reportId, DailyReportReviewRequest request) {
        SensorDailyReport report = getOrThrow(reportId);
        if (SensorDailyReport.STATUS_REVIEWED.equals(report.getStatus())) {
            return report;
        }
        SensorDailyReport before = copyOf(report);
        report.setStatus(SensorDailyReport.STATUS_REVIEWED);
        report.setReviewedBy(request.getReviewedBy());
        report.setReviewedTime(LocalDateTime.now());
        report.setReviewRemark(request.getReviewRemark());
        reportMapper.updateById(report);
        evidenceService.record(SensorChangeEvidence.ENTITY_DAILY_REPORT, report.getId(),
                SensorChangeEvidence.ACTION_REVIEW, request.getRequestId(), request.getReviewedBy(),
                before, report,
                "日报审核通过，池塘id=" + report.getPondId() + "，日期=" + report.getReportDate()
                        + "，纳入读数=" + report.getReadingCount() + "条；区间冻结，"
                        + "此后迟到数据只能拒收留存（LATE_REJECTED），不再改动本报结论");
        return report;
    }

    public SensorDailyReport getOrThrow(Long id) {
        SensorDailyReport report = reportMapper.selectById(id);
        if (report == null) {
            throw new IllegalArgumentException("水质日报不存在，id=" + id);
        }
        return report;
    }

    public SensorDailyReport findNullable(Long pondId, LocalDate reportDate) {
        List<SensorDailyReport> rows = reportMapper.selectList(new QueryWrapper<SensorDailyReport>()
                .eq("pond_id", pondId).eq("report_date", reportDate));
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<SensorDailyReport> list(Long pondId, LocalDate reportDate, String status) {
        QueryWrapper<SensorDailyReport> wrapper = new QueryWrapper<>();
        wrapper.eq(pondId != null, "pond_id", pondId);
        wrapper.eq(reportDate != null, "report_date", reportDate);
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.orderByAsc("report_date", "pond_id");
        return reportMapper.selectList(wrapper);
    }

    private void applyMetricStats(SensorDailyReport report, List<SensorReading> included) {
        if (included.isEmpty()) {
            report.setAvgSalinityPpt(null);
            report.setMinSalinityPpt(null);
            report.setMaxSalinityPpt(null);
            report.setAvgDissolvedOxygenMgL(null);
            report.setMinDissolvedOxygenMgL(null);
            report.setMaxDissolvedOxygenMgL(null);
            report.setAvgWaterTempC(null);
            report.setMinWaterTempC(null);
            report.setMaxWaterTempC(null);
            return;
        }
        BigDecimal sumSalinity = BigDecimal.ZERO;
        BigDecimal sumDo = BigDecimal.ZERO;
        BigDecimal sumTemp = BigDecimal.ZERO;
        BigDecimal minSalinity = null, maxSalinity = null;
        BigDecimal minDo = null, maxDo = null;
        BigDecimal minTemp = null, maxTemp = null;
        for (SensorReading r : included) {
            sumSalinity = sumSalinity.add(r.getSalinityPpt());
            sumDo = sumDo.add(r.getDissolvedOxygenMgL());
            sumTemp = sumTemp.add(r.getWaterTempC());
            minSalinity = minOf(minSalinity, r.getSalinityPpt());
            maxSalinity = maxOf(maxSalinity, r.getSalinityPpt());
            minDo = minOf(minDo, r.getDissolvedOxygenMgL());
            maxDo = maxOf(maxDo, r.getDissolvedOxygenMgL());
            minTemp = minOf(minTemp, r.getWaterTempC());
            maxTemp = maxOf(maxTemp, r.getWaterTempC());
        }
        BigDecimal count = BigDecimal.valueOf(included.size());
        report.setAvgSalinityPpt(sumSalinity.divide(count, AVG_SCALE, RoundingMode.HALF_UP));
        report.setMinSalinityPpt(minSalinity);
        report.setMaxSalinityPpt(maxSalinity);
        report.setAvgDissolvedOxygenMgL(sumDo.divide(count, AVG_SCALE, RoundingMode.HALF_UP));
        report.setMinDissolvedOxygenMgL(minDo);
        report.setMaxDissolvedOxygenMgL(maxDo);
        report.setAvgWaterTempC(sumTemp.divide(count, AVG_SCALE, RoundingMode.HALF_UP));
        report.setMinWaterTempC(minTemp);
        report.setMaxWaterTempC(maxTemp);
    }

    private BigDecimal minOf(BigDecimal current, BigDecimal value) {
        return current == null || value.compareTo(current) < 0 ? value : current;
    }

    private BigDecimal maxOf(BigDecimal current, BigDecimal value) {
        return current == null || value.compareTo(current) > 0 ? value : current;
    }

    private SensorDailyReport copyOf(SensorDailyReport s) {
        SensorDailyReport c = new SensorDailyReport();
        c.setId(s.getId());
        c.setPondId(s.getPondId());
        c.setReportDate(s.getReportDate());
        c.setReadingCount(s.getReadingCount());
        c.setAvgSalinityPpt(s.getAvgSalinityPpt());
        c.setMinSalinityPpt(s.getMinSalinityPpt());
        c.setMaxSalinityPpt(s.getMaxSalinityPpt());
        c.setAvgDissolvedOxygenMgL(s.getAvgDissolvedOxygenMgL());
        c.setMinDissolvedOxygenMgL(s.getMinDissolvedOxygenMgL());
        c.setMaxDissolvedOxygenMgL(s.getMaxDissolvedOxygenMgL());
        c.setAvgWaterTempC(s.getAvgWaterTempC());
        c.setMinWaterTempC(s.getMinWaterTempC());
        c.setMaxWaterTempC(s.getMaxWaterTempC());
        c.setAbnormalPendingCount(s.getAbnormalPendingCount());
        c.setStatus(s.getStatus());
        c.setReviewedBy(s.getReviewedBy());
        c.setReviewedTime(s.getReviewedTime());
        c.setReviewRemark(s.getReviewRemark());
        c.setRuleVersionId(s.getRuleVersionId());
        return c;
    }
}
