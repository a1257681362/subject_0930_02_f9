package com.evops.aqua.sensor.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 归并读数：服务端按「设备 + 设备时间 + 序号」归并，唯一键保证重复上传不产生第二行。
 * NORMAL 正常计入日报；ABNORMAL_PENDING 异常待复核（暂不计入）；
 * ABNORMAL_CONFIRMED 复核确认异常（不计入）；REVIEWED_NORMAL 复核改判正常（计入）；
 * LATE_REJECTED 迟到且所属日报区间已审核（留存不计入，已审核结论不被改动）。
 * ruleVersionId 冻结归并当时生效规则版本，历史读数不随新规则回改。
 */
@TableName("t_sensor_reading")
public class SensorReading extends BaseEntity {

    public static final String STATUS_NORMAL = "NORMAL";
    public static final String STATUS_ABNORMAL_PENDING = "ABNORMAL_PENDING";
    public static final String STATUS_ABNORMAL_CONFIRMED = "ABNORMAL_CONFIRMED";
    public static final String STATUS_REVIEWED_NORMAL = "REVIEWED_NORMAL";
    public static final String STATUS_LATE_REJECTED = "LATE_REJECTED";

    private Long packetId;
    private Long deviceId;
    private Long pondId;
    private LocalDateTime deviceTime;
    private Integer seqNo;
    private BigDecimal salinityPpt;
    private BigDecimal dissolvedOxygenMgL;
    private BigDecimal waterTempC;
    private LocalDate reportDate;
    private String status;
    private Boolean includedFlag;
    private String abnormalMetrics;
    private Long ruleVersionId;

    public Long getPacketId() { return packetId; }
    public void setPacketId(Long packetId) { this.packetId = packetId; }
    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public LocalDateTime getDeviceTime() { return deviceTime; }
    public void setDeviceTime(LocalDateTime deviceTime) { this.deviceTime = deviceTime; }
    public Integer getSeqNo() { return seqNo; }
    public void setSeqNo(Integer seqNo) { this.seqNo = seqNo; }
    public BigDecimal getSalinityPpt() { return salinityPpt; }
    public void setSalinityPpt(BigDecimal salinityPpt) { this.salinityPpt = salinityPpt; }
    public BigDecimal getDissolvedOxygenMgL() { return dissolvedOxygenMgL; }
    public void setDissolvedOxygenMgL(BigDecimal dissolvedOxygenMgL) { this.dissolvedOxygenMgL = dissolvedOxygenMgL; }
    public BigDecimal getWaterTempC() { return waterTempC; }
    public void setWaterTempC(BigDecimal waterTempC) { this.waterTempC = waterTempC; }
    public LocalDate getReportDate() { return reportDate; }
    public void setReportDate(LocalDate reportDate) { this.reportDate = reportDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Boolean getIncludedFlag() { return includedFlag; }
    public void setIncludedFlag(Boolean includedFlag) { this.includedFlag = includedFlag; }
    public String getAbnormalMetrics() { return abnormalMetrics; }
    public void setAbnormalMetrics(String abnormalMetrics) { this.abnormalMetrics = abnormalMetrics; }
    public Long getRuleVersionId() { return ruleVersionId; }
    public void setRuleVersionId(Long ruleVersionId) { this.ruleVersionId = ruleVersionId; }
}
