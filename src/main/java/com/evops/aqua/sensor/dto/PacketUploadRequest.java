package com.evops.aqua.sensor.dto;

import javax.validation.constraints.NotBlank;

/**
 * 设备离线批量上传包：rawPayload 为设备原始报文（JSON：deviceCode/packetNo/readings[]），
 * signature 为设备用密钥对原文计算的 HMAC-SHA256（hex）。
 * 签名失败或原文无法解析整包隔离并保留原文；requestId 与（设备+包号）双幂等，
 * 重复上传返回首次归并结果，不重复计入日报。
 */
public class PacketUploadRequest {

    @NotBlank(message = "设备编码不能为空")
    private String deviceCode;

    @NotBlank(message = "包号不能为空")
    private String packetNo;

    @NotBlank(message = "原始报文不能为空")
    private String rawPayload;

    @NotBlank(message = "签名不能为空")
    private String signature;

    private String requestId;

    private String receivedBy;

    public String getDeviceCode() { return deviceCode; }
    public void setDeviceCode(String deviceCode) { this.deviceCode = deviceCode; }
    public String getPacketNo() { return packetNo; }
    public void setPacketNo(String packetNo) { this.packetNo = packetNo; }
    public String getRawPayload() { return rawPayload; }
    public void setRawPayload(String rawPayload) { this.rawPayload = rawPayload; }
    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getReceivedBy() { return receivedBy; }
    public void setReceivedBy(String receivedBy) { this.receivedBy = receivedBy; }
}
