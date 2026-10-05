package com.evops.aqua.sensor.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 传感器模块变更证据：设备登记/更换、规则版本发布/停用、上传包归并/隔离、
 * 读数归并/迟到拒收、异常复核、日报审核的所有写操作留痕，只追加，operator 处理人不丢。
 */
@TableName("t_sensor_change_evidence")
public class SensorChangeEvidence extends BaseEntity {

    public static final String ENTITY_DEVICE = "DEVICE";
    public static final String ENTITY_RULE_VERSION = "RULE_VERSION";
    public static final String ENTITY_PACKET = "PACKET";
    public static final String ENTITY_READING = "READING";
    public static final String ENTITY_REVIEW = "REVIEW";
    public static final String ENTITY_DAILY_REPORT = "DAILY_REPORT";

    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_REPLACE = "REPLACE";
    public static final String ACTION_PUBLISH = "PUBLISH";
    public static final String ACTION_SUPERSEDE = "SUPERSEDE";
    public static final String ACTION_MERGE = "MERGE";
    public static final String ACTION_QUARANTINE = "QUARANTINE";
    public static final String ACTION_LATE_REJECT = "LATE_REJECT";
    public static final String ACTION_FLAG = "FLAG";
    public static final String ACTION_REVIEW = "REVIEW";
    public static final String ACTION_UPDATE = "UPDATE";

    private String entityType;
    private Long entityId;
    private String action;
    private String requestId;
    private String operator;
    private String beforeJson;
    private String afterJson;
    private String detail;

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getBeforeJson() { return beforeJson; }
    public void setBeforeJson(String beforeJson) { this.beforeJson = beforeJson; }
    public String getAfterJson() { return afterJson; }
    public void setAfterJson(String afterJson) { this.afterJson = afterJson; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
}
