package com.evops.aqua.sensor.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 异常读数复核：confirmedAbnormal=true 确认异常（保持剔除日报）；
 * confirmedAbnormal=false 改判正常（仅当所属日报区间未审核才补入日报）。
 * 复核人与复核理由必填；requestId 幂等，重复复核返回首次结果。
 */
public class ReviewDecisionRequest {

    @NotNull(message = "复核结论不能为空")
    private Boolean confirmedAbnormal;

    @NotBlank(message = "复核人不能为空")
    private String reviewedBy;

    @NotBlank(message = "复核理由不能为空")
    private String reviewReason;

    private String requestId;

    public Boolean getConfirmedAbnormal() { return confirmedAbnormal; }
    public void setConfirmedAbnormal(Boolean confirmedAbnormal) { this.confirmedAbnormal = confirmedAbnormal; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public String getReviewReason() { return reviewReason; }
    public void setReviewReason(String reviewReason) { this.reviewReason = reviewReason; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
