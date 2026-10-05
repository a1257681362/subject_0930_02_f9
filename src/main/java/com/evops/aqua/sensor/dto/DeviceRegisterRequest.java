package com.evops.aqua.sensor.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 登记水质传感器设备：按池塘安装并下发 HMAC 签名密钥。
 * requestId 幂等：重复登记返回首次设备，不产生第二条。
 */
public class DeviceRegisterRequest {

    @NotBlank(message = "设备编码不能为空")
    @Size(max = 40, message = "设备编码最长40位")
    private String deviceCode;

    @NotBlank(message = "设备名称不能为空")
    private String deviceName;

    @NotNull(message = "所属池塘不能为空")
    private Long pondId;

    @NotBlank(message = "设备签名密钥不能为空")
    private String secretKey;

    @NotNull(message = "安装日期不能为空")
    private LocalDate installedDate;

    private String requestId;

    private String operator;

    private String remark;

    public String getDeviceCode() { return deviceCode; }
    public void setDeviceCode(String deviceCode) { this.deviceCode = deviceCode; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public LocalDate getInstalledDate() { return installedDate; }
    public void setInstalledDate(LocalDate installedDate) { this.installedDate = installedDate; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
