package com.evops.aqua.sensor.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 异常读数待复核队列：归并时超出当时生效规则版本阈值的读数进队，复核前不计入日报。
 * 复核必须登记复核人与理由；确认异常则保持剔除；改判正常仅当所属日报区间未审核（OPEN）
 * 才补入日报，已审核区间的旧结论不被改动。每读数至多一条队列记录。
 */
@TableName("t_sensor_review_queue")
public class SensorReviewItem extends BaseEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_CONFIRMED_ABNORMAL = "CONFIRMED_ABNORMAL";
    public static final String STATUS_REVIEWED_NORMAL = "REVIEWED_NORMAL";

    private Long readingId;
    private Long pondId;
    private Long deviceId;
    private LocalDateTime deviceTime;
    private String abnormalMetrics;
    private String readingSnapshot;
    private String status;
    private String reviewedBy;
    private String reviewReason;
    private LocalDateTime reviewedTime;
    private String requestId;

    public Long getReadingId() { return readingId; }
    public void setReadingId(Long readingId) { this.readingId = readingId; }
    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
    public LocalDateTime getDeviceTime() { return deviceTime; }
    public void setDeviceTime(LocalDateTime deviceTime) { this.deviceTime = deviceTime; }
    public String getAbnormalMetrics() { return abnormalMetrics; }
    public void setAbnormalMetrics(String abnormalMetrics) { this.abnormalMetrics = abnormalMetrics; }
    public String getReadingSnapshot() { return readingSnapshot; }
    public void setReadingSnapshot(String readingSnapshot) { this.readingSnapshot = readingSnapshot; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public String getReviewReason() { return reviewReason; }
    public void setReviewReason(String reviewReason) { this.reviewReason = reviewReason; }
    public LocalDateTime getReviewedTime() { return reviewedTime; }
    public void setReviewedTime(LocalDateTime reviewedTime) { this.reviewedTime = reviewedTime; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
