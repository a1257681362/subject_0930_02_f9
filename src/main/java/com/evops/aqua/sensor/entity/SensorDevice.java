package com.evops.aqua.sensor.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 水质传感器设备档案：按池塘安装，持有 HMAC 签名密钥。
 * 更换设备建立新旧关联：新设备 predecessorDeviceId 指向旧设备，旧设备置 REPLACED 并回指新设备；
 * 旧设备的离线积压数据仍允许归并（密钥保留），池塘数据序列跨设备延续。
 */
@TableName("t_sensor_device")
public class SensorDevice extends BaseEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_REPLACED = "REPLACED";

    private String deviceCode;
    private String deviceName;
    private Long pondId;
    private String secretKey;
    private String status;
    private Long predecessorDeviceId;
    private Long replacedByDeviceId;
    private LocalDate installedDate;
    private LocalDateTime replacedTime;
    private String replaceReason;
    private String requestId;
    private String remark;

    public String getDeviceCode() { return deviceCode; }
    public void setDeviceCode(String deviceCode) { this.deviceCode = deviceCode; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getPredecessorDeviceId() { return predecessorDeviceId; }
    public void setPredecessorDeviceId(Long predecessorDeviceId) { this.predecessorDeviceId = predecessorDeviceId; }
    public Long getReplacedByDeviceId() { return replacedByDeviceId; }
    public void setReplacedByDeviceId(Long replacedByDeviceId) { this.replacedByDeviceId = replacedByDeviceId; }
    public LocalDate getInstalledDate() { return installedDate; }
    public void setInstalledDate(LocalDate installedDate) { this.installedDate = installedDate; }
    public LocalDateTime getReplacedTime() { return replacedTime; }
    public void setReplacedTime(LocalDateTime replacedTime) { this.replacedTime = replacedTime; }
    public String getReplaceReason() { return replaceReason; }
    public void setReplaceReason(String replaceReason) { this.replaceReason = replaceReason; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
