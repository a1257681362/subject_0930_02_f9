package com.evops.aqua.sensor.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.sensor.dto.DeviceRegisterRequest;
import com.evops.aqua.sensor.dto.DeviceReplaceRequest;
import com.evops.aqua.sensor.entity.SensorChangeEvidence;
import com.evops.aqua.sensor.entity.SensorDevice;
import com.evops.aqua.sensor.mapper.SensorDeviceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 水质传感器设备：登记（requestId 幂等）与更换。
 * 更换建立新旧关联：新设备 predecessor 指向旧设备、旧设备置 REPLACED 并回指新设备，
 * 更换原因必填；旧设备的离线积压数据仍允许归并（密钥保留），池塘数据序列跨设备延续。
 */
@Service
public class SensorDeviceService {

    private final SensorDeviceMapper deviceMapper;
    private final SensorEvidenceService evidenceService;

    public SensorDeviceService(SensorDeviceMapper deviceMapper, SensorEvidenceService evidenceService) {
        this.deviceMapper = deviceMapper;
        this.evidenceService = evidenceService;
    }

    /**
     * 登记设备。幂等：相同 requestId 重复登记返回首次设备；设备编码全局唯一。
     */
    @Transactional(rollbackFor = Exception.class)
    public SensorDevice register(DeviceRegisterRequest request) {
        if (request.getRequestId() != null && !request.getRequestId().trim().isEmpty()) {
            List<SensorDevice> same = deviceMapper.selectList(
                    new QueryWrapper<SensorDevice>().eq("request_id", request.getRequestId()));
            if (!same.isEmpty()) {
                return same.get(0);
            }
        }
        Long duplicated = deviceMapper.selectCount(
                new QueryWrapper<SensorDevice>().eq("device_code", request.getDeviceCode()));
        if (duplicated != null && duplicated > 0) {
            throw new IllegalArgumentException("设备编码已存在：" + request.getDeviceCode());
        }

        SensorDevice device = new SensorDevice();
        device.setDeviceCode(request.getDeviceCode());
        device.setDeviceName(request.getDeviceName());
        device.setPondId(request.getPondId());
        device.setSecretKey(request.getSecretKey());
        device.setStatus(SensorDevice.STATUS_ACTIVE);
        device.setInstalledDate(request.getInstalledDate());
        device.setRequestId(request.getRequestId());
        device.setRemark(request.getRemark());
        deviceMapper.insert(device);

        evidenceService.record(SensorChangeEvidence.ENTITY_DEVICE, device.getId(),
                SensorChangeEvidence.ACTION_CREATE, request.getRequestId(), request.getOperator(),
                null, device,
                "登记水质传感器设备：" + device.getDeviceCode() + "（" + device.getDeviceName()
                        + "），池塘id=" + device.getPondId() + "，安装日=" + device.getInstalledDate());
        return device;
    }

    /**
     * 设备更换：同池登记新设备并建立新旧关联。旧设备必须为 ACTIVE；
     * 新设备 predecessor 指向旧设备，旧设备置 REPLACED 并回指新设备，更换原因必填。
     * 幂等：相同 requestId 重复请求返回首次更换结果（新设备），不重复更换。
     */
    @Transactional(rollbackFor = Exception.class)
    public SensorDevice replace(Long oldDeviceId, DeviceReplaceRequest request) {
        if (request.getRequestId() != null && !request.getRequestId().trim().isEmpty()) {
            List<SensorDevice> same = deviceMapper.selectList(
                    new QueryWrapper<SensorDevice>().eq("request_id", request.getRequestId()));
            if (!same.isEmpty()) {
                return same.get(0);
            }
        }
        SensorDevice oldDevice = getOrThrow(oldDeviceId);
        if (!SensorDevice.STATUS_ACTIVE.equals(oldDevice.getStatus())) {
            throw new IllegalArgumentException("设备" + oldDevice.getDeviceCode()
                    + "已不是在用状态（" + oldDevice.getStatus() + "），不能再次更换");
        }
        Long duplicated = deviceMapper.selectCount(
                new QueryWrapper<SensorDevice>().eq("device_code", request.getNewDeviceCode()));
        if (duplicated != null && duplicated > 0) {
            throw new IllegalArgumentException("新设备编码已存在：" + request.getNewDeviceCode());
        }

        SensorDevice newDevice = new SensorDevice();
        newDevice.setDeviceCode(request.getNewDeviceCode());
        newDevice.setDeviceName(request.getNewDeviceName());
        newDevice.setPondId(oldDevice.getPondId());
        newDevice.setSecretKey(request.getSecretKey());
        newDevice.setStatus(SensorDevice.STATUS_ACTIVE);
        newDevice.setPredecessorDeviceId(oldDevice.getId());
        newDevice.setInstalledDate(request.getInstalledDate() == null
                ? LocalDate.now() : request.getInstalledDate());
        newDevice.setRequestId(request.getRequestId());
        newDevice.setRemark("由设备" + oldDevice.getDeviceCode() + "更换而来：" + request.getReason());
        deviceMapper.insert(newDevice);

        SensorDevice oldBefore = copyOf(oldDevice);
        oldDevice.setStatus(SensorDevice.STATUS_REPLACED);
        oldDevice.setReplacedByDeviceId(newDevice.getId());
        oldDevice.setReplacedTime(LocalDateTime.now());
        oldDevice.setReplaceReason(request.getReason());
        deviceMapper.updateById(oldDevice);

        evidenceService.record(SensorChangeEvidence.ENTITY_DEVICE, newDevice.getId(),
                SensorChangeEvidence.ACTION_CREATE, request.getRequestId(), request.getOperator(),
                null, newDevice,
                "更换新设备：" + newDevice.getDeviceCode() + "，predecessor=旧设备"
                        + oldDevice.getDeviceCode() + "(id=" + oldDevice.getId() + ")，新旧关联已建立");
        evidenceService.record(SensorChangeEvidence.ENTITY_DEVICE, oldDevice.getId(),
                SensorChangeEvidence.ACTION_REPLACE, request.getRequestId(), request.getOperator(),
                oldBefore, oldDevice,
                "旧设备" + oldDevice.getDeviceCode() + "置 REPLACED，由新设备" + newDevice.getDeviceCode()
                        + "(id=" + newDevice.getId() + ")接替；更换原因：" + request.getReason()
                        + "；旧设备离线积压数据仍可归并");
        return newDevice;
    }

    public SensorDevice getOrThrow(Long id) {
        SensorDevice device = deviceMapper.selectById(id);
        if (device == null) {
            throw new IllegalArgumentException("传感器设备不存在，id=" + id);
        }
        return device;
    }

    public SensorDevice getByCodeOrThrow(String deviceCode) {
        List<SensorDevice> devices = deviceMapper.selectList(
                new QueryWrapper<SensorDevice>().eq("device_code", deviceCode));
        if (devices.isEmpty()) {
            throw new IllegalArgumentException("传感器设备不存在，deviceCode=" + deviceCode);
        }
        return devices.get(0);
    }

    public List<SensorDevice> list(Long pondId, String status) {
        QueryWrapper<SensorDevice> wrapper = new QueryWrapper<>();
        wrapper.eq(pondId != null, "pond_id", pondId);
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.orderByAsc("id");
        return deviceMapper.selectList(wrapper);
    }

    /**
     * 新旧关联链：沿 predecessor 向前、replacedBy 向后串起同池历任设备，
     * 供池塘数据序列跨设备追溯（旧设备离线积压数据归并后序列不断档）。
     */
    public Map<String, Object> lineage(Long deviceId) {
        SensorDevice device = getOrThrow(deviceId);
        List<SensorDevice> chain = new ArrayList<>();
        SensorDevice cursor = device;
        while (cursor.getPredecessorDeviceId() != null) {
            cursor = getOrThrow(cursor.getPredecessorDeviceId());
        }
        chain.add(cursor);
        while (cursor.getReplacedByDeviceId() != null) {
            cursor = getOrThrow(cursor.getReplacedByDeviceId());
            chain.add(cursor);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deviceId", deviceId);
        result.put("pondId", device.getPondId());
        result.put("chainSize", chain.size());
        result.put("chain", chain);
        return result;
    }

    private SensorDevice copyOf(SensorDevice s) {
        SensorDevice c = new SensorDevice();
        c.setId(s.getId());
        c.setDeviceCode(s.getDeviceCode());
        c.setDeviceName(s.getDeviceName());
        c.setPondId(s.getPondId());
        c.setSecretKey(s.getSecretKey());
        c.setStatus(s.getStatus());
        c.setPredecessorDeviceId(s.getPredecessorDeviceId());
        c.setReplacedByDeviceId(s.getReplacedByDeviceId());
        c.setInstalledDate(s.getInstalledDate());
        c.setReplacedTime(s.getReplacedTime());
        c.setReplaceReason(s.getReplaceReason());
        c.setRequestId(s.getRequestId());
        c.setRemark(s.getRemark());
        return c;
    }
}
