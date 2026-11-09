package com.qrpublic.apartment.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Zalo webhook signature helper.
 *
 * Zalo signs every webhook delivery with
 * {@code mac = sha256(appId + data + timeStamp + oaSecretKey)} and sends the
 * digest in the {@code X-ZEvent-Signature} header. The signature is the only
 * trustworthy proof of origin in this deployment: nginx, the gateway and the app
 * all share one host, so the socket address of an inbound webhook is always
 * loopback and proves nothing.
 */
public final class ZaloSignatureUtils {

    private ZaloSignatureUtils() {
    }

    /** Lower-case hex sha256 of the concatenation Zalo uses for its mac. */
    public static String sign(String appId, String data, String timeStamp, String oaSecretKey) {
        String payload = nullToEmpty(appId) + nullToEmpty(data) + nullToEmpty(timeStamp) + nullToEmpty(oaSecretKey);
        return sha256Hex(payload);
    }

    /**
     * Constant-time comparison against the value sent by Zalo. Accepts both the
     * documented {@code X-ZEvent-Signature} header and a {@code mac} body field,
     * which older OA applications still use.
     */
    public static boolean matches(String expectedHex, String providedHex) {
        if (expectedHex == null || providedHex == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expectedHex.trim().toLowerCase().getBytes(StandardCharsets.UTF_8),
                providedHex.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(nullToEmpty(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(Character.forDigit((b >> 4) & 0xF, 16));
                builder.append(Character.forDigit(b & 0xF, 16));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
