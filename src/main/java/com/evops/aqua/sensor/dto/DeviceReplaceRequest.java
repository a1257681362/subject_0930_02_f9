package com.evops.aqua.sensor.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 设备更换：在同池登记新设备并建立新旧关联（新设备 predecessor 指向旧设备，旧设备置 REPLACED）。
 * 更换原因必填；requestId 幂等，重复请求返回首次更换结果。
 */
public class DeviceReplaceRequest {

    @NotBlank(message = "新设备编码不能为空")
    @Size(max = 40, message = "新设备编码最长40位")
    private String newDeviceCode;

    @NotBlank(message = "新设备名称不能为空")
    private String newDeviceName;

    @NotBlank(message = "新设备签名密钥不能为空")
    private String secretKey;

    @NotBlank(message = "更换原因不能为空")
    private String reason;

    private LocalDate installedDate;

    private String requestId;

    private String operator;

    public String getNewDeviceCode() { return newDeviceCode; }
    public void setNewDeviceCode(String newDeviceCode) { this.newDeviceCode = newDeviceCode; }
    public String getNewDeviceName() { return newDeviceName; }
    public void setNewDeviceName(String newDeviceName) { this.newDeviceName = newDeviceName; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDate getInstalledDate() { return installedDate; }
    public void setInstalledDate(LocalDate installedDate) { this.installedDate = installedDate; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
}
