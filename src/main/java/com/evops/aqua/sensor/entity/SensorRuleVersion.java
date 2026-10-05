package com.evops.aqua.sensor.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 归并规则版本：盐度/溶氧/温度正常范围（闭区间），用于归并时判定异常读数。
 * 规则调整只能生成新版本：发布后旧 ACTIVE 版本置 SUPERSEDED，无修改/删除入口；
 * 已归并的包/读数/日报冻结当时版本指针，离线归并旧结论不随新版本回改。
 */
@TableName("t_sensor_rule_version")
public class SensorRuleVersion extends BaseEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_SUPERSEDED = "SUPERSEDED";

    private String versionNo;
    private BigDecimal salinityMin;
    private BigDecimal salinityMax;
    private BigDecimal doMin;
    private BigDecimal doMax;
    private BigDecimal tempMin;
    private BigDecimal tempMax;
    private String status;
    private String publishedBy;
    private LocalDateTime publishedTime;
    private String requestId;
    private String remark;

    public String getVersionNo() { return versionNo; }
    public void setVersionNo(String versionNo) { this.versionNo = versionNo; }
    public BigDecimal getSalinityMin() { return salinityMin; }
    public void setSalinityMin(BigDecimal salinityMin) { this.salinityMin = salinityMin; }
    public BigDecimal getSalinityMax() { return salinityMax; }
    public void setSalinityMax(BigDecimal salinityMax) { this.salinityMax = salinityMax; }
    public BigDecimal getDoMin() { return doMin; }
    public void setDoMin(BigDecimal doMin) { this.doMin = doMin; }
    public BigDecimal getDoMax() { return doMax; }
    public void setDoMax(BigDecimal doMax) { this.doMax = doMax; }
    public BigDecimal getTempMin() { return tempMin; }
    public void setTempMin(BigDecimal tempMin) { this.tempMin = tempMin; }
    public BigDecimal getTempMax() { return tempMax; }
    public void setTempMax(BigDecimal tempMax) { this.tempMax = tempMax; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPublishedBy() { return publishedBy; }
    public void setPublishedBy(String publishedBy) { this.publishedBy = publishedBy; }
    public LocalDateTime getPublishedTime() { return publishedTime; }
    public void setPublishedTime(LocalDateTime publishedTime) { this.publishedTime = publishedTime; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
