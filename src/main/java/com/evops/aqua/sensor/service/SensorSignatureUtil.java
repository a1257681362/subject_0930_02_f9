package com.evops.aqua.sensor.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * 设备包签名工具：HMAC-SHA256(原始报文, 设备密钥) 的 hex 串，比较时忽略大小写。
 */
public final class SensorSignatureUtil {

    private SensorSignatureUtil() {
    }

    public static String hmacSha256Hex(String rawPayload, String secretKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(rawPayload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("计算设备包签名失败", ex);
        }
    }

    public static boolean matches(String rawPayload, String secretKey, String signature) {
        if (signature == null || signature.trim().isEmpty()) {
            return false;
        }
        return hmacSha256Hex(rawPayload, secretKey).equalsIgnoreCase(signature.trim());
    }
}
