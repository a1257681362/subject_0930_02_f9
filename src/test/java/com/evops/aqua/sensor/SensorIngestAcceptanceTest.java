package com.evops.aqua.sensor;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.sensor.entity.SensorChangeEvidence;
import com.evops.aqua.sensor.entity.SensorDailyReport;
import com.evops.aqua.sensor.entity.SensorDevice;
import com.evops.aqua.sensor.entity.SensorPacket;
import com.evops.aqua.sensor.entity.SensorReading;
import com.evops.aqua.sensor.entity.SensorReviewItem;
import com.evops.aqua.sensor.entity.SensorRuleVersion;
import com.evops.aqua.sensor.mapper.SensorChangeEvidenceMapper;
import com.evops.aqua.sensor.mapper.SensorDailyReportMapper;
import com.evops.aqua.sensor.mapper.SensorDeviceMapper;
import com.evops.aqua.sensor.mapper.SensorPacketMapper;
import com.evops.aqua.sensor.mapper.SensorReadingMapper;
import com.evops.aqua.sensor.mapper.SensorReviewItemMapper;
import com.evops.aqua.sensor.mapper.SensorRuleVersionMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * 池塘水质传感器接入验收。
 * 主线：发布归并规则v1 → 登记设备 → 离线批量上传（按设备时间+序号归并、生成日报）
 * → 重复上传不重复计入日报 → 混合包（重复+新读数+异常）→ 异常读数进待复核队列
 * → 复核改判正常补入未审核区间 / 确认异常保持剔除 → 日报审核冻结区间
 * → 迟到数据只能拒收留存、只能补充未审核区间 → 设备更换建立新旧关联、旧设备积压仍可归并
 * → 签名失败整包隔离保留原文 → 规则调整只生成新版本、不改离线归并旧结论 → 全程留证。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SensorIngestAcceptanceTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private SensorDeviceMapper deviceMapper;
    @Autowired
    private SensorRuleVersionMapper ruleMapper;
    @Autowired
    private SensorPacketMapper packetMapper;
    @Autowired
    private SensorReadingMapper readingMapper;
    @Autowired
    private SensorReviewItemMapper reviewItemMapper;
    @Autowired
    private SensorDailyReportMapper reportMapper;
    @Autowired
    private SensorChangeEvidenceMapper evidenceMapper;

    private final List<String> report = new ArrayList<>();
    private String sessionCookie;

    private Long pondId;
    private Long ruleV1Id;
    private Long device1Id;
    private Long device2Id;
    private Long packet1Id;
    private Long reviewItem1Id;
    private Long reviewItem2Id;
    private Long report1001Id;

    private static final String DEVICE1 = "WQ-SENSOR-01";
    private static final String DEVICE2 = "WQ-SENSOR-02";
    private static final String KEY1 = "secret-key-01";
    private static final String KEY2 = "secret-key-02";
    private static final LocalDate D1001 = LocalDate.of(2026, 10, 1);
    private static final LocalDate D1002 = LocalDate.of(2026, 10, 2);
    private static final LocalDate D1003 = LocalDate.of(2026, 10, 3);

    @Test
    void acceptanceSensorIngest() throws Exception {
        checkpoint("CP0", "登录认证", () -> {
            Map<String, Object> body = new HashMap<>();
            body.put("username", "bootstrap");
            body.put("password", "bootstrap");
            ResponseEntity<String> resp = call(HttpMethod.POST, "/api/auth/login", body);
            assertSuccess(json(resp), "登录");
            List<String> cookies = resp.getHeaders().get(HttpHeaders.SET_COOKIE);
            check(cookies != null && !cookies.isEmpty(), "登录成功但未下发会话Cookie");
            sessionCookie = cookies.get(0).split(";")[0];
        });

        checkpoint("CP1", "建养殖池并发布归并规则v1（幂等）", () -> {
            Map<String, Object> pond = new HashMap<>();
            pond.put("pondCode", "SN-P01");
            pond.put("pondName", "传感器1号池");
            pond.put("seaArea", "东山湾海区");
            pond.put("waterDepthM", "2.50");
            pond.put("waterAreaM2", "1200.00");
            pond.put("salinityPpt", "28.5");
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/ponds", pond));
            assertSuccess(resp, "建池");
            pondId = resp.path("data").path("id").asLong();

            JsonNode rule = json(call(HttpMethod.POST, "/api/aqua/sensor/rule-versions",
                    ruleBody("WQ-RULE-V1", "25", "34", "5", "9", "18", "30", "REQ-RULE-V1")));
            assertSuccess(rule, "发布规则v1");
            ruleV1Id = rule.path("data").path("id").asLong();
            check("ACTIVE".equals(rule.path("data").path("status").asText()), "规则v1未处于ACTIVE");

            JsonNode replay = json(call(HttpMethod.POST, "/api/aqua/sensor/rule-versions",
                    ruleBody("WQ-RULE-V1-IGNORED", "1", "2", "1", "2", "1", "2", "REQ-RULE-V1")));
            assertSuccess(replay, "规则重复发布幂等");
            check(replay.path("data").path("id").asLong() == ruleV1Id,
                    "相同requestId重复发布规则应返回首次版本");
            check(ruleMapper.selectCount(new QueryWrapper<SensorRuleVersion>()
                    .eq("status", "ACTIVE")) == 1, "应只有一个ACTIVE规则版本");
        });

        checkpoint("CP2", "登记传感器设备（幂等）", () -> {
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/sensor/devices",
                    deviceBody(DEVICE1, "水质一体机1号", KEY1, "REQ-DEV-01")));
            assertSuccess(resp, "登记设备");
            device1Id = resp.path("data").path("id").asLong();

            JsonNode replay = json(call(HttpMethod.POST, "/api/aqua/sensor/devices",
                    deviceBody(DEVICE1, "水质一体机1号", KEY1, "REQ-DEV-01")));
            assertSuccess(replay, "设备重复登记幂等");
            check(replay.path("data").path("id").asLong() == device1Id, "重复登记应返回首次设备");
            check(deviceMapper.selectCount(new QueryWrapper<SensorDevice>()
                    .eq("device_code", DEVICE1)) == 1, "设备编码应唯一");
        });

        checkpoint("CP3", "设备离线批量上传：按设备时间+序号归并并生成日报", () -> {
            String payload = packetPayload(DEVICE1, "PKT-1001",
                    reading(1, "2026-10-01T08:00:00", "28.50", "6.20", "24.30"),
                    reading(2, "2026-10-01T12:00:00", "28.60", "6.10", "25.10"),
                    reading(3, "2026-10-01T18:00:00", "28.55", "6.30", "24.80"),
                    reading(4, "2026-10-02T08:00:00", "28.70", "6.40", "24.50"));
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/sensor/packets",
                    uploadBody(DEVICE1, "PKT-1001", payload, sign(payload, KEY1), "REQ-PKT-1001")));
            assertSuccess(resp, "上传包PKT-1001");
            JsonNode packet = resp.path("data").path("packet");
            check("MERGED".equals(packet.path("status").asText()), "包应归并成功");
            check(packet.path("mergedCount").asInt() == 4, "应归并4条读数");
            check(packet.path("ruleVersionId").asLong() == ruleV1Id, "包应冻结规则v1");
            packet1Id = packet.path("id").asLong();

            SensorDailyReport r1 = reportOf(pondId, D1001);
            check(r1 != null && r1.getReadingCount() == 3, "10-01日报应纳入3条");
            check(r1.getAvgSalinityPpt().compareTo(new BigDecimal("28.550")) == 0, "10-01平均盐度应为28.550");
            check("OPEN".equals(r1.getStatus()), "日报应为未审核OPEN");
            check(r1.getRuleVersionId().equals(ruleV1Id), "日报应冻结规则v1");
            report1001Id = r1.getId();
            SensorDailyReport r2 = reportOf(pondId, D1002);
            check(r2 != null && r2.getReadingCount() == 1, "10-02日报应纳入1条");
        });

        checkpoint("CP4", "重复上传同一包：不重复落行、不重复计入日报", () -> {
            String payload = packetPayload(DEVICE1, "PKT-1001",
                    reading(1, "2026-10-01T08:00:00", "28.50", "6.20", "24.30"),
                    reading(2, "2026-10-01T12:00:00", "28.60", "6.10", "25.10"),
                    reading(3, "2026-10-01T18:00:00", "28.55", "6.30", "24.80"),
                    reading(4, "2026-10-02T08:00:00", "28.70", "6.40", "24.50"));
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/sensor/packets",
                    uploadBody(DEVICE1, "PKT-1001", payload, sign(payload, KEY1), "REQ-PKT-1001")));
            assertSuccess(resp, "重复上传同一包");
            check(resp.path("data").path("idempotentReplay").asBoolean(), "应识别为幂等重放");
            check(resp.path("data").path("packet").path("id").asLong() == packet1Id, "应返回首次包");

            JsonNode replay2 = json(call(HttpMethod.POST, "/api/aqua/sensor/packets",
                    uploadBody(DEVICE1, "PKT-1001", payload, sign(payload, KEY1), "REQ-PKT-1001-RETRY")));
            assertSuccess(replay2, "换requestId重复上传同包号");
            check(replay2.path("data").path("packet").path("id").asLong() == packet1Id,
                    "设备+包号命中应返回首次包");

            check(readingMapper.selectCount(new QueryWrapper<SensorReading>()
                    .eq("device_id", device1Id)) == 4, "读数不应重复落行");
            check(reportOf(pondId, D1001).getReadingCount() == 3, "日报不应重复计入");
            check(packetMapper.selectCount(new QueryWrapper<SensorPacket>()
                    .eq("device_id", device1Id)) == 1, "不应产生第二个包");
        });

        checkpoint("CP5", "混合包：重复读数去重、新读数归并、异常读数进待复核队列", () -> {
            String payload = packetPayload(DEVICE1, "PKT-1002",
                    reading(1, "2026-10-01T08:00:00", "28.50", "6.20", "24.30"),
                    reading(2, "2026-10-01T12:00:00", "28.60", "6.10", "25.10"),
                    reading(5, "2026-10-01T20:00:00", "28.65", "6.25", "24.60"),
                    reading(6, "2026-10-01T21:00:00", "28.60", "3.50", "24.40"));
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/sensor/packets",
                    uploadBody(DEVICE1, "PKT-1002", payload, sign(payload, KEY1), "REQ-PKT-1002")));
            assertSuccess(resp, "上传混合包PKT-1002");
            JsonNode packet = resp.path("data").path("packet");
            check(packet.path("duplicateCount").asInt() == 2, "应识别2条重复读数");
            check(packet.path("mergedCount").asInt() == 1, "应新归并1条");
            check(packet.path("abnormalCount").asInt() == 1, "应识别1条异常（溶氧3.5低于下限5）");

            List<SensorReviewItem> queue = reviewItemMapper.selectList(
                    new QueryWrapper<SensorReviewItem>().eq("status", "PENDING"));
            check(queue.size() == 1, "待复核队列应有1条");
            check("DO".equals(queue.get(0).getAbnormalMetrics()), "异常指标应为DO");
            reviewItem1Id = queue.get(0).getId();

            SensorDailyReport r1 = reportOf(pondId, D1001);
            check(r1.getReadingCount() == 4, "10-01日报应纳入4条（异常待复核不计入）");
            check(r1.getAbnormalPendingCount() == 1, "10-01日报应记1条待复核异常");
            SensorReading abnormal = readingMapper.selectById(queue.get(0).getReadingId());
            check(!abnormal.getIncludedFlag(), "异常读数复核前不应计入日报");
        });

        checkpoint("CP6", "异常复核：理由必填，改判正常补入未审核区间", () -> {
            Map<String, Object> noReason = new HashMap<>();
            noReason.put("confirmedAbnormal", false);
            noReason.put("reviewedBy", "复核员-陈");
            noReason.put("requestId", "REQ-REV-1-X");
            JsonNode rejected = json(call(HttpMethod.POST,
                    "/api/aqua/sensor/review-queue/" + reviewItem1Id + "/review", noReason));
            check(!rejected.path("success").asBoolean(), "复核理由必填应拒绝");

            JsonNode resp = json(call(HttpMethod.POST,
                    "/api/aqua/sensor/review-queue/" + reviewItem1Id + "/review",
                    reviewBody(false, "复核员-陈", "现场复测溶氧正常，初判为探头气泡干扰", "REQ-REV-1")));
            assertSuccess(resp, "复核改判正常");
            check("REVIEWED_NORMAL".equals(resp.path("data").path("status").asText()), "队列项应改判正常");

            SensorDailyReport r1 = reportOf(pondId, D1001);
            check(r1.getReadingCount() == 5, "改判正常后10-01日报应补入为5条");
            check(r1.getAbnormalPendingCount() == 0, "待复核计数应清零");

            JsonNode replay = json(call(HttpMethod.POST,
                    "/api/aqua/sensor/review-queue/" + reviewItem1Id + "/review",
                    reviewBody(false, "复核员-陈", "现场复测溶氧正常，初判为探头气泡干扰", "REQ-REV-1")));
            assertSuccess(replay, "同requestId复核幂等");
            JsonNode again = json(call(HttpMethod.POST,
                    "/api/aqua/sensor/review-queue/" + reviewItem1Id + "/review",
                    reviewBody(true, "复核员-陈", "重复复核", "REQ-REV-1-OTHER")));
            check(!again.path("success").asBoolean(), "已复核项不允许换requestId重复复核");
        });

        checkpoint("CP7", "异常确认：保持剔除日报", () -> {
            String payload = packetPayload(DEVICE1, "PKT-1003",
                    reading(7, "2026-10-02T09:00:00", "28.60", "6.30", "33.00"));
            JsonNode up = json(call(HttpMethod.POST, "/api/aqua/sensor/packets",
                    uploadBody(DEVICE1, "PKT-1003", payload, sign(payload, KEY1), "REQ-PKT-1003")));
            assertSuccess(up, "上传包PKT-1003");
            check(up.path("data").path("packet").path("abnormalCount").asInt() == 1,
                    "温度33超上限30应判异常");

            List<SensorReviewItem> queue = reviewItemMapper.selectList(
                    new QueryWrapper<SensorReviewItem>().eq("status", "PENDING"));
            check(queue.size() == 1, "待复核队列应有1条");
            check("TEMP".equals(queue.get(0).getAbnormalMetrics()), "异常指标应为TEMP");
            reviewItem2Id = queue.get(0).getId();

            JsonNode resp = json(call(HttpMethod.POST,
                    "/api/aqua/sensor/review-queue/" + reviewItem2Id + "/review",
                    reviewBody(true, "复核员-陈", "高温属实，已开增氧机并换水", "REQ-REV-2")));
            assertSuccess(resp, "复核确认异常");
            check("CONFIRMED_ABNORMAL".equals(resp.path("data").path("status").asText()),
                    "队列项应确认异常");
            SensorDailyReport r2 = reportOf(pondId, D1002);
            check(r2.getReadingCount() == 1, "确认异常后10-02日报仍只纳入1条");
            check(r2.getAbnormalPendingCount() == 0, "10-02待复核计数应清零");
        });

        checkpoint("CP8", "日报审核：审核人必填，通过后区间冻结且幂等", () -> {
            JsonNode noReviewer = json(call(HttpMethod.POST,
                    "/api/aqua/sensor/daily-reports/" + report1001Id + "/review",
                    mapOf("requestId", "REQ-DR-X")));
            check(!noReviewer.path("success").asBoolean(), "审核人必填应拒绝");

            JsonNode resp = json(call(HttpMethod.POST,
                    "/api/aqua/sensor/daily-reports/" + report1001Id + "/review",
                    reportReviewBody("主管-王", "数据完整，同意归档", "REQ-DR-1001")));
            assertSuccess(resp, "日报审核");
            check("REVIEWED".equals(resp.path("data").path("status").asText()), "日报应审核冻结");
            check("主管-王".equals(resp.path("data").path("reviewedBy").asText()), "审核人应留痕");

            JsonNode replay = json(call(HttpMethod.POST,
                    "/api/aqua/sensor/daily-reports/" + report1001Id + "/review",
                    reportReviewBody("主管-王", "重复审核", "REQ-DR-1001-AGAIN")));
            assertSuccess(replay, "重复审核幂等");
            check(replay.path("data").path("reviewedBy").asText().equals("主管-王"),
                    "重复审核不应覆盖首次审核人");
        });

        checkpoint("CP9", "迟到数据只能补充未审核区间：已审核区间拒收留存", () -> {
            String payload = packetPayload(DEVICE1, "PKT-1004",
                    reading(8, "2026-10-01T22:00:00", "28.60", "6.20", "24.50"),
                    reading(9, "2026-10-03T08:00:00", "28.50", "6.10", "24.20"));
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/sensor/packets",
                    uploadBody(DEVICE1, "PKT-1004", payload, sign(payload, KEY1), "REQ-PKT-1004")));
            assertSuccess(resp, "上传包含迟到读数的包");
            JsonNode packet = resp.path("data").path("packet");
            check(packet.path("lateRejectedCount").asInt() == 1, "已审核区间的迟到读数应拒收");
            check(packet.path("mergedCount").asInt() == 1, "未审核区间的读数应正常归并");

            SensorDailyReport r1 = reportOf(pondId, D1001);
            check(r1.getReadingCount() == 5 && "REVIEWED".equals(r1.getStatus()),
                    "已审核的10-01日报不应被迟到数据改动");
            SensorReading late = readingMapper.selectOne(new QueryWrapper<SensorReading>()
                    .eq("device_id", device1Id).eq("seq_no", 8));
            check(late != null && "LATE_REJECTED".equals(late.getStatus()) && !late.getIncludedFlag(),
                    "迟到读数应留存为LATE_REJECTED且不计入");
            SensorDailyReport r3 = reportOf(pondId, D1003);
            check(r3 != null && r3.getReadingCount() == 1, "未审核的10-03区间应被补充");
        });

        checkpoint("CP10", "设备更换建立新旧关联，旧设备离线积压仍可归并", () -> {
            Map<String, Object> replace = new HashMap<>();
            replace.put("newDeviceCode", DEVICE2);
            replace.put("newDeviceName", "水质一体机2号");
            replace.put("secretKey", KEY2);
            replace.put("reason", "探头老化更换");
            replace.put("installedDate", "2026-10-03");
            replace.put("operator", "设备员-郑");
            replace.put("requestId", "REQ-REPLACE-1");
            JsonNode resp = json(call(HttpMethod.POST,
                    "/api/aqua/sensor/devices/" + device1Id + "/replace", replace));
            assertSuccess(resp, "设备更换");
            device2Id = resp.path("data").path("id").asLong();
            check(resp.path("data").path("predecessorDeviceId").asLong() == device1Id,
                    "新设备predecessor应指向旧设备");

            SensorDevice oldDevice = deviceMapper.selectById(device1Id);
            check("REPLACED".equals(oldDevice.getStatus()), "旧设备应置REPLACED");
            check(device2Id.equals(oldDevice.getReplacedByDeviceId()), "旧设备应回指新设备");

            JsonNode lineage = json(call(HttpMethod.GET,
                    "/api/aqua/sensor/devices/" + device2Id + "/lineage", null));
            assertSuccess(lineage, "新旧关联链");
            check(lineage.path("data").path("chainSize").asInt() == 2, "关联链应含新旧两台设备");
            check(DEVICE1.equals(lineage.path("data").path("chain").get(0).path("deviceCode").asText())
                    && DEVICE2.equals(lineage.path("data").path("chain").get(1).path("deviceCode").asText()),
                    "关联链顺序应为旧→新");

            JsonNode again = json(call(HttpMethod.POST,
                    "/api/aqua/sensor/devices/" + device1Id + "/replace", replace));
            assertSuccess(again, "同requestId更换幂等");
            check(again.path("data").path("id").asLong() == device2Id, "重复更换应返回首次新设备");
            Map<String, Object> other = new HashMap<>(replace);
            other.put("newDeviceCode", "WQ-SENSOR-03");
            other.put("requestId", "REQ-REPLACE-2");
            JsonNode rejected = json(call(HttpMethod.POST,
                    "/api/aqua/sensor/devices/" + device1Id + "/replace", other));
            check(!rejected.path("success").asBoolean(), "已REPLACED设备不允许再次更换");

            String payload = packetPayload(DEVICE1, "PKT-1005",
                    reading(10, "2026-10-03T09:00:00", "28.55", "6.15", "24.10"));
            JsonNode backlog = json(call(HttpMethod.POST, "/api/aqua/sensor/packets",
                    uploadBody(DEVICE1, "PKT-1005", payload, sign(payload, KEY1), "REQ-PKT-1005")));
            assertSuccess(backlog, "旧设备离线积压包仍应归并");
            check(backlog.path("data").path("packet").path("mergedCount").asInt() == 1,
                    "旧设备积压读数应归并");
            check(reportOf(pondId, D1003).getReadingCount() == 2,
                    "池塘数据序列应跨设备延续（10-03纳入2条）");
        });

        checkpoint("CP11", "签名失败的包整包隔离并保留原文", () -> {
            String payload = packetPayload(DEVICE2, "PKT-2001",
                    reading(1, "2026-10-03T10:00:00", "28.50", "6.20", "24.30"));
            JsonNode resp = json(call(HttpMethod.POST, "/api/aqua/sensor/packets",
                    uploadBody(DEVICE2, "PKT-2001", payload, "deadbeefdeadbeef", "REQ-PKT-2001")));
            assertSuccess(resp, "签名失败包应被接收并隔离");
            JsonNode packet = resp.path("data").path("packet");
            check("QUARANTINED".equals(packet.path("status").asText()), "签名失败应整包隔离");
            check(!packet.path("signatureOk").asBoolean(), "签名校验应为不通过");
            check(payload.equals(packet.path("rawPayload").asText()), "隔离包应保留原文");
            check(packet.path("quarantineReason").asText().contains("签名校验失败"), "应记录隔离原因");

            SensorPacket stored = packetMapper.selectById(packet.path("id").asLong());
            check(payload.equals(stored.getRawPayload()), "落库隔离包原文不丢");
            check(readingMapper.selectCount(new QueryWrapper<SensorReading>()
                    .eq("packet_id", stored.getId())) == 0, "隔离包不应归并任何读数");
            check(reportOf(pondId, D1003).getReadingCount() == 2, "隔离包不应影响日报");

            JsonNode replay = json(call(HttpMethod.POST, "/api/aqua/sensor/packets",
                    uploadBody(DEVICE2, "PKT-2001", payload, "deadbeefdeadbeef", "REQ-PKT-2001")));
            assertSuccess(replay, "隔离包重复上传幂等");
            check(replay.path("data").path("packet").path("id").asLong() == stored.getId(),
                    "重复上传应返回首次隔离包");
            check(packetMapper.selectCount(new QueryWrapper<SensorPacket>()
                    .eq("status", "QUARANTINED")) == 1, "不应产生第二个隔离包");
        });

        checkpoint("CP12", "规则调整只生成新版本，不改离线归并旧结论", () -> {
            JsonNode v2 = json(call(HttpMethod.POST, "/api/aqua/sensor/rule-versions",
                    ruleBody("WQ-RULE-V2", "25", "32", "5", "9", "18", "30", "REQ-RULE-V2")));
            assertSuccess(v2, "发布规则v2");
            Long ruleV2Id = v2.path("data").path("id").asLong();

            SensorRuleVersion v1 = ruleMapper.selectById(ruleV1Id);
            check("SUPERSEDED".equals(v1.getStatus()), "旧版本应停用");
            check(v1.getSalinityMax().compareTo(new BigDecimal("34.00")) == 0,
                    "旧版本内容不应被改写");
            check("ACTIVE".equals(json(call(HttpMethod.GET, "/api/aqua/sensor/rule-versions/active", null))
                    .path("data").path("status").asText()), "当前生效应为新版本");

            // 盐度33在v1（上限34）下正常、在v2（上限32）下异常：新包按v2判定
            String payload = packetPayload(DEVICE2, "PKT-2002",
                    reading(1, "2026-10-04T08:00:00", "33.00", "6.20", "24.30"));
            JsonNode up = json(call(HttpMethod.POST, "/api/aqua/sensor/packets",
                    uploadBody(DEVICE2, "PKT-2002", payload, sign(payload, KEY2), "REQ-PKT-2002")));
            assertSuccess(up, "v2下上传包");
            check(up.path("data").path("packet").path("abnormalCount").asInt() == 1,
                    "盐度33应按v2判异常进待复核队列");
            check(up.path("data").path("packet").path("ruleVersionId").asLong() == ruleV2Id,
                    "新包应冻结规则v2");

            // 离线归并旧结论不改：已审核日报、历史读数、历史包仍冻结v1
            SensorDailyReport r1 = reportOf(pondId, D1001);
            check("REVIEWED".equals(r1.getStatus()) && r1.getReadingCount() == 5
                    && r1.getRuleVersionId().equals(ruleV1Id), "已审核日报结论不应随v2改动");
            List<SensorReading> oldReadings = readingMapper.selectList(new QueryWrapper<SensorReading>()
                    .eq("packet_id", packet1Id));
            check(oldReadings.size() == 4, "历史读数不应增减");
            for (SensorReading r : oldReadings) {
                check("NORMAL".equals(r.getStatus()) && r.getRuleVersionId().equals(ruleV1Id),
                        "历史读数状态与冻结规则不应随v2回改");
            }
            SensorDailyReport r3 = reportOf(pondId, D1003);
            check(r3.getReadingCount() == 2, "未审核区间历史纳入数不应被v2回改");
        });

        checkpoint("CP13", "全程留证：按对象/动作/requestId可追溯，处理人不丢", () -> {
            check(countEvidence("PACKET", "QUARANTINE", null) >= 1, "应有隔离留证");
            check(countEvidence("READING", "LATE_REJECT", null) >= 1, "应有迟到拒收留证");
            check(countEvidence("DEVICE", "REPLACE", null) >= 1, "应有设备更换留证");
            check(countEvidence("RULE_VERSION", "SUPERSEDE", null) >= 1, "应有旧规则停用留证");
            check(countEvidence("DAILY_REPORT", "REVIEW", null) >= 1, "应有日报审核留证");

            List<SensorChangeEvidence> p1 = evidenceMapper.selectList(
                    new QueryWrapper<SensorChangeEvidence>().eq("request_id", "REQ-PKT-1001"));
            check(!p1.isEmpty(), "应按requestId追溯到PKT-1001归并留证");
            check(p1.stream().allMatch(e -> "接收员-周".equals(e.getOperator())),
                    "归并留证应保留接收人");

            List<SensorChangeEvidence> replaced = evidenceMapper.selectList(
                    new QueryWrapper<SensorChangeEvidence>()
                            .eq("entity_type", "DEVICE").eq("action", "REPLACE"));
            check(replaced.stream().allMatch(e -> "设备员-郑".equals(e.getOperator())),
                    "设备更换留证应保留操作人");
        });

        printReport();
    }

    // ------------------------------------------------------------
    // 数据构造与断言辅助
    // ------------------------------------------------------------

    private Map<String, Object> ruleBody(String versionNo, String salMin, String salMax,
                                         String doMin, String doMax, String tempMin, String tempMax,
                                         String requestId) {
        Map<String, Object> m = new HashMap<>();
        m.put("versionNo", versionNo);
        m.put("salinityMin", salMin);
        m.put("salinityMax", salMax);
        m.put("doMin", doMin);
        m.put("doMax", doMax);
        m.put("tempMin", tempMin);
        m.put("tempMax", tempMax);
        m.put("publishedBy", "质检主管-林");
        m.put("requestId", requestId);
        return m;
    }

    private Map<String, Object> deviceBody(String code, String name, String key, String requestId) {
        Map<String, Object> m = new HashMap<>();
        m.put("deviceCode", code);
        m.put("deviceName", name);
        m.put("pondId", pondId);
        m.put("secretKey", key);
        m.put("installedDate", "2026-09-20");
        m.put("operator", "设备员-郑");
        m.put("requestId", requestId);
        return m;
    }

    private Map<String, Object> reading(int seqNo, String deviceTime,
                                        String salinity, String dissolvedOxygen, String temp) {
        Map<String, Object> m = new HashMap<>();
        m.put("seqNo", seqNo);
        m.put("deviceTime", deviceTime);
        m.put("salinityPpt", new BigDecimal(salinity));
        m.put("dissolvedOxygenMgL", new BigDecimal(dissolvedOxygen));
        m.put("waterTempC", new BigDecimal(temp));
        return m;
    }

    private String packetPayload(String deviceCode, String packetNo, Map<String, Object>... readings)
            throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("deviceCode", deviceCode);
        m.put("packetNo", packetNo);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> r : readings) {
            list.add(r);
        }
        m.put("readings", list);
        return objectMapper.writeValueAsString(m);
    }

    private Map<String, Object> uploadBody(String deviceCode, String packetNo, String rawPayload,
                                           String signature, String requestId) {
        Map<String, Object> m = new HashMap<>();
        m.put("deviceCode", deviceCode);
        m.put("packetNo", packetNo);
        m.put("rawPayload", rawPayload);
        m.put("signature", signature);
        m.put("requestId", requestId);
        m.put("receivedBy", "接收员-周");
        return m;
    }

    private Map<String, Object> reviewBody(boolean confirmedAbnormal, String reviewedBy,
                                           String reason, String requestId) {
        Map<String, Object> m = new HashMap<>();
        m.put("confirmedAbnormal", confirmedAbnormal);
        m.put("reviewedBy", reviewedBy);
        m.put("reviewReason", reason);
        m.put("requestId", requestId);
        return m;
    }

    private Map<String, Object> reportReviewBody(String reviewedBy, String remark, String requestId) {
        Map<String, Object> m = new HashMap<>();
        m.put("reviewedBy", reviewedBy);
        m.put("reviewRemark", remark);
        m.put("requestId", requestId);
        return m;
    }

    private Map<String, Object> mapOf(String k, Object v) {
        Map<String, Object> m = new HashMap<>();
        m.put(k, v);
        return m;
    }

    /** 与设备侧一致的 HMAC-SHA256(hex) 签名。 */
    private String sign(String rawPayload, String secretKey) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(rawPayload.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private SensorDailyReport reportOf(Long pondId, LocalDate date) {
        List<SensorDailyReport> rows = reportMapper.selectList(new QueryWrapper<SensorDailyReport>()
                .eq("pond_id", pondId).eq("report_date", date));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private long countEvidence(String entityType, String action, String requestId) {
        QueryWrapper<SensorChangeEvidence> w = new QueryWrapper<SensorChangeEvidence>()
                .eq("entity_type", entityType).eq("action", action);
        if (requestId != null) {
            w.eq("request_id", requestId);
        }
        Long c = evidenceMapper.selectCount(w);
        return c == null ? 0L : c;
    }

    private void checkpoint(String id, String name, CheckpointBody body) {
        try {
            body.run();
            String line = "通过 " + id + " " + name;
            report.add(line);
            System.out.println("[验收] " + line);
        } catch (Throwable t) {
            String stop = "停点 " + id + "「" + name + "」失败，原因：" + t.getMessage();
            report.add(stop);
            report.add("验收结论：未通过，后续检查点未执行");
            printReport();
            fail("验收" + stop);
        }
    }

    private void printReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n========== 池塘水质传感器接入验收报告 ==========\n");
        for (String line : report) {
            sb.append(line).append('\n');
        }
        sb.append("=============================================\n");
        System.out.println(sb);
    }

    private ResponseEntity<String> call(HttpMethod method, String url, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (sessionCookie != null) {
            headers.add(HttpHeaders.COOKIE, sessionCookie);
        }
        return rest.exchange(url, method, new HttpEntity<>(body, headers), String.class);
    }

    private JsonNode json(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }

    private void assertSuccess(JsonNode node, String what) {
        if (!node.path("success").asBoolean(false)) {
            throw new IllegalStateException(what + "接口返回失败：" + node.path("message").asText());
        }
    }

    private void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private interface CheckpointBody {
        void run() throws Exception;
    }
}
