package com.evops.aqua.sensor.dto;

import javax.validation.constraints.NotBlank;

/**
 * 日报审核：审核人必填，审核通过后区间冻结（REVIEWED）。
 * 冻结后迟到数据只能标记 LATE_REJECTED 留存，不再改动本报结论；重复审核幂等不多写。
 */
public class DailyReportReviewRequest {

    @NotBlank(message = "审核人不能为空")
    private String reviewedBy;

    private String reviewRemark;

    private String requestId;

    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public String getReviewRemark() { return reviewRemark; }
    public void setReviewRemark(String reviewRemark) { this.reviewRemark = reviewRemark; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
