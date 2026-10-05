package com.evops.aqua.sensor.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 设备上传包：设备离线后批量上传的一包读数。
 * 签名校验失败（或原文无法解析、包内设备与上传设备不一致）整包隔离 QUARANTINED，
 * rawPayload 保留原文不丢、不做任何归并；正常归并后置 MERGED 并记录各项计数。
 * requestId 与（设备 + 包号）双幂等：重复上传返回首次归并结果，不重复归并。
 */
@TableName("t_sensor_packet")
public class SensorPacket extends BaseEntity {

    public static final String STATUS_MERGED = "MERGED";
    public static final String STATUS_QUARANTINED = "QUARANTINED";

    private String packetNo;
    private Long deviceId;
    private String requestId;
    private String signature;
    private Boolean signatureOk;
    private String status;
    private String rawPayload;
    private Integer totalCount;
    private Integer mergedCount;
    private Integer duplicateCount;
    private Integer lateRejectedCount;
    private Integer abnormalCount;
    private Long ruleVersionId;
    private String receivedBy;
    private String quarantineReason;
    private String remark;

    public String getPacketNo() { return packetNo; }
    public void setPacketNo(String packetNo) { this.packetNo = packetNo; }
    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }
    public Boolean getSignatureOk() { return signatureOk; }
    public void setSignatureOk(Boolean signatureOk) { this.signatureOk = signatureOk; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRawPayload() { return rawPayload; }
    public void setRawPayload(String rawPayload) { this.rawPayload = rawPayload; }
    public Integer getTotalCount() { return totalCount; }
    public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }
    public Integer getMergedCount() { return mergedCount; }
    public void setMergedCount(Integer mergedCount) { this.mergedCount = mergedCount; }
    public Integer getDuplicateCount() { return duplicateCount; }
    public void setDuplicateCount(Integer duplicateCount) { this.duplicateCount = duplicateCount; }
    public Integer getLateRejectedCount() { return lateRejectedCount; }
    public void setLateRejectedCount(Integer lateRejectedCount) { this.lateRejectedCount = lateRejectedCount; }
    public Integer getAbnormalCount() { return abnormalCount; }
    public void setAbnormalCount(Integer abnormalCount) { this.abnormalCount = abnormalCount; }
    public Long getRuleVersionId() { return ruleVersionId; }
    public void setRuleVersionId(Long ruleVersionId) { this.ruleVersionId = ruleVersionId; }
    public String getReceivedBy() { return receivedBy; }
    public void setReceivedBy(String receivedBy) { this.receivedBy = receivedBy; }
    public String getQuarantineReason() { return quarantineReason; }
    public void setQuarantineReason(String quarantineReason) { this.quarantineReason = quarantineReason; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
