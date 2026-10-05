package com.evops.aqua.sensor.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.sensor.dto.SensorRulePublishRequest;
import com.evops.aqua.sensor.entity.SensorChangeEvidence;
import com.evops.aqua.sensor.entity.SensorRuleVersion;
import com.evops.aqua.sensor.mapper.SensorRuleVersionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 归并规则版本：盐度/溶氧/温度正常范围。
 * 规则调整只能生成新版本——发布后旧 ACTIVE 版本置 SUPERSEDED，无修改/删除入口；
 * 已归并的包/读数/日报冻结当时版本指针，离线归并旧结论不随新版本回改。
 */
@Service
public class SensorRuleVersionService {

    private final SensorRuleVersionMapper ruleMapper;
    private final SensorEvidenceService evidenceService;

    public SensorRuleVersionService(SensorRuleVersionMapper ruleMapper, SensorEvidenceService evidenceService) {
        this.ruleMapper = ruleMapper;
        this.evidenceService = evidenceService;
    }

    /**
     * 发布规则新版本。幂等：相同 requestId 重复发布返回首次版本；版本号全局唯一。
     * 发布后旧 ACTIVE 版本自动置 SUPERSEDED（只生成新版本，不改写旧版本内容）。
     */
    @Transactional(rollbackFor = Exception.class)
    public SensorRuleVersion publish(SensorRulePublishRequest request) {
        if (request.getSalinityMin().compareTo(request.getSalinityMax()) > 0
                || request.getDoMin().compareTo(request.getDoMax()) > 0
                || request.getTempMin().compareTo(request.getTempMax()) > 0) {
            throw new IllegalArgumentException("规则范围下限不能大于上限");
        }
        if (request.getRequestId() != null && !request.getRequestId().trim().isEmpty()) {
            List<SensorRuleVersion> same = ruleMapper.selectList(
                    new QueryWrapper<SensorRuleVersion>().eq("request_id", request.getRequestId()));
            if (!same.isEmpty()) {
                return same.get(0);
            }
        }
        Long duplicated = ruleMapper.selectCount(
                new QueryWrapper<SensorRuleVersion>().eq("version_no", request.getVersionNo()));
        if (duplicated != null && duplicated > 0) {
            throw new IllegalArgumentException("规则版本号已存在：" + request.getVersionNo());
        }

        SensorRuleVersion version = new SensorRuleVersion();
        version.setVersionNo(request.getVersionNo());
        version.setSalinityMin(request.getSalinityMin());
        version.setSalinityMax(request.getSalinityMax());
        version.setDoMin(request.getDoMin());
        version.setDoMax(request.getDoMax());
        version.setTempMin(request.getTempMin());
        version.setTempMax(request.getTempMax());
        version.setStatus(SensorRuleVersion.STATUS_ACTIVE);
        version.setPublishedBy(request.getPublishedBy());
        version.setPublishedTime(LocalDateTime.now());
        version.setRequestId(request.getRequestId());
        version.setRemark(request.getRemark());
        ruleMapper.insert(version);

        // 旧 ACTIVE 版本停用：规则调整只生成新版本，旧版本内容保留不改
        List<SensorRuleVersion> actives = ruleMapper.selectList(
                new QueryWrapper<SensorRuleVersion>()
                        .eq("status", SensorRuleVersion.STATUS_ACTIVE)
                        .ne("id", version.getId()));
        for (SensorRuleVersion old : actives) {
            SensorRuleVersion before = copyOf(old);
            old.setStatus(SensorRuleVersion.STATUS_SUPERSEDED);
            ruleMapper.updateById(old);
            evidenceService.record(SensorChangeEvidence.ENTITY_RULE_VERSION, old.getId(),
                    SensorChangeEvidence.ACTION_SUPERSEDE, request.getRequestId(),
                    request.getPublishedBy(), before, old,
                    "发布新规则版本" + version.getVersionNo() + "，旧版本" + old.getVersionNo()
                            + "停用；已按旧版本归并的读数与日报结论不回改");
        }

        evidenceService.record(SensorChangeEvidence.ENTITY_RULE_VERSION, version.getId(),
                SensorChangeEvidence.ACTION_PUBLISH, request.getRequestId(), request.getPublishedBy(),
                null, version,
                "发布归并规则版本" + version.getVersionNo() + "：盐度[" + version.getSalinityMin() + ","
                        + version.getSalinityMax() + "]，溶氧[" + version.getDoMin() + ","
                        + version.getDoMax() + "]，温度[" + version.getTempMin() + ","
                        + version.getTempMax() + "]；只作用于发布后归并的包");
        return version;
    }

    /**
     * 当前生效（ACTIVE）规则版本；没有则拒绝（须先发布规则再归并上传包）。
     */
    public SensorRuleVersion currentActive() {
        List<SensorRuleVersion> actives = ruleMapper.selectList(
                new QueryWrapper<SensorRuleVersion>()
                        .eq("status", SensorRuleVersion.STATUS_ACTIVE)
                        .orderByDesc("id").last("LIMIT 1"));
        if (actives.isEmpty()) {
            throw new IllegalArgumentException("尚无生效的归并规则版本，请先发布规则版本");
        }
        return actives.get(0);
    }

    public SensorRuleVersion getOrThrow(Long id) {
        SensorRuleVersion version = ruleMapper.selectById(id);
        if (version == null) {
            throw new IllegalArgumentException("归并规则版本不存在，id=" + id);
        }
        return version;
    }

    public List<SensorRuleVersion> list(String status) {
        QueryWrapper<SensorRuleVersion> wrapper = new QueryWrapper<>();
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.orderByAsc("id");
        return ruleMapper.selectList(wrapper);
    }

    private SensorRuleVersion copyOf(SensorRuleVersion s) {
        SensorRuleVersion c = new SensorRuleVersion();
        c.setId(s.getId());
        c.setVersionNo(s.getVersionNo());
        c.setSalinityMin(s.getSalinityMin());
        c.setSalinityMax(s.getSalinityMax());
        c.setDoMin(s.getDoMin());
        c.setDoMax(s.getDoMax());
        c.setTempMin(s.getTempMin());
        c.setTempMax(s.getTempMax());
        c.setStatus(s.getStatus());
        c.setPublishedBy(s.getPublishedBy());
        c.setPublishedTime(s.getPublishedTime());
        c.setRequestId(s.getRequestId());
        c.setRemark(s.getRemark());
        return c;
    }
}
