package com.evops.aqua.sensor.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 水质日报：按池按日汇总已纳入读数（NORMAL + REVIEWED_NORMAL）。
 * OPEN（未审核）时可被后续归并/复核重算；REVIEWED（已审核）后冻结——
 * 迟到数据一律 LATE_REJECTED 不再改动本报，离线归并旧结论不被规则新版本回改。
 * 重复上传靠读数唯一键（设备+设备时间+序号）保证不重复计入。
 */
@TableName("t_sensor_daily_report")
public class SensorDailyReport extends BaseEntity {

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_REVIEWED = "REVIEWED";

    private Long pondId;
    private LocalDate reportDate;
    private Integer readingCount;
    private BigDecimal avgSalinityPpt;
    private BigDecimal minSalinityPpt;
    private BigDecimal maxSalinityPpt;
    private BigDecimal avgDissolvedOxygenMgL;
    private BigDecimal minDissolvedOxygenMgL;
    private BigDecimal maxDissolvedOxygenMgL;
    private BigDecimal avgWaterTempC;
    private BigDecimal minWaterTempC;
    private BigDecimal maxWaterTempC;
    private Integer abnormalPendingCount;
    private String status;
    private String reviewedBy;
    private LocalDateTime reviewedTime;
    private String reviewRemark;
    private Long ruleVersionId;

    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public LocalDate getReportDate() { return reportDate; }
    public void setReportDate(LocalDate reportDate) { this.reportDate = reportDate; }
    public Integer getReadingCount() { return readingCount; }
    public void setReadingCount(Integer readingCount) { this.readingCount = readingCount; }
    public BigDecimal getAvgSalinityPpt() { return avgSalinityPpt; }
    public void setAvgSalinityPpt(BigDecimal avgSalinityPpt) { this.avgSalinityPpt = avgSalinityPpt; }
    public BigDecimal getMinSalinityPpt() { return minSalinityPpt; }
    public void setMinSalinityPpt(BigDecimal minSalinityPpt) { this.minSalinityPpt = minSalinityPpt; }
    public BigDecimal getMaxSalinityPpt() { return maxSalinityPpt; }
    public void setMaxSalinityPpt(BigDecimal maxSalinityPpt) { this.maxSalinityPpt = maxSalinityPpt; }
    public BigDecimal getAvgDissolvedOxygenMgL() { return avgDissolvedOxygenMgL; }
    public void setAvgDissolvedOxygenMgL(BigDecimal avgDissolvedOxygenMgL) { this.avgDissolvedOxygenMgL = avgDissolvedOxygenMgL; }
    public BigDecimal getMinDissolvedOxygenMgL() { return minDissolvedOxygenMgL; }
    public void setMinDissolvedOxygenMgL(BigDecimal minDissolvedOxygenMgL) { this.minDissolvedOxygenMgL = minDissolvedOxygenMgL; }
    public BigDecimal getMaxDissolvedOxygenMgL() { return maxDissolvedOxygenMgL; }
    public void setMaxDissolvedOxygenMgL(BigDecimal maxDissolvedOxygenMgL) { this.maxDissolvedOxygenMgL = maxDissolvedOxygenMgL; }
    public BigDecimal getAvgWaterTempC() { return avgWaterTempC; }
    public void setAvgWaterTempC(BigDecimal avgWaterTempC) { this.avgWaterTempC = avgWaterTempC; }
    public BigDecimal getMinWaterTempC() { return minWaterTempC; }
    public void setMinWaterTempC(BigDecimal minWaterTempC) { this.minWaterTempC = minWaterTempC; }
    public BigDecimal getMaxWaterTempC() { return maxWaterTempC; }
    public void setMaxWaterTempC(BigDecimal maxWaterTempC) { this.maxWaterTempC = maxWaterTempC; }
    public Integer getAbnormalPendingCount() { return abnormalPendingCount; }
    public void setAbnormalPendingCount(Integer abnormalPendingCount) { this.abnormalPendingCount = abnormalPendingCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public LocalDateTime getReviewedTime() { return reviewedTime; }
    public void setReviewedTime(LocalDateTime reviewedTime) { this.reviewedTime = reviewedTime; }
    public String getReviewRemark() { return reviewRemark; }
    public void setReviewRemark(String reviewRemark) { this.reviewRemark = reviewRemark; }
    public Long getRuleVersionId() { return ruleVersionId; }
    public void setRuleVersionId(Long ruleVersionId) { this.ruleVersionId = ruleVersionId; }
}
