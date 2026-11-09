package com.qrpublic.apartment.chat.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Client identity helpers for the app chat.
 *
 * A browser cannot expose a MAC address and an IP is unstable (Vietnamese
 * mobile
 * users sit behind carrier-grade NAT and share addresses), so a returning
 * visitor
 * is recognised by, in order of confidence: the persisted device id, then a
 * salted attribute fingerprint, then the hashed IP. Fingerprints are stored as
 * per-attribute salted digests so individual browser attributes are never kept
 * in
 * clear text while still allowing partial matching.
 */
public final class ChatIdentityUtils {

    /**
     * Hex characters kept per attribute digest: enough to compare, small enough to
     * index.
     */
    private static final int TOKEN_WIDTH = 12;

    private ChatIdentityUtils() {
    }

    /**
     * Builds the comparable fingerprint token: {@code h1:h2:h3...}, one salted
     * digest per supplied attribute. Blank attributes become empty slots so the
     * component count stays stable for a given client build.
     */
    public static String buildFingerprintToken(List<String> attributes, String salt) {
        List<String> tokens = new ArrayList<>();
        if (attributes != null) {
            for (String attribute : attributes) {
                tokens.add(hashAttribute(attribute, salt));
            }
        }
        return String.join(":", tokens);
    }

    private static String hashAttribute(String attribute, String salt) {
        if (attribute == null || attribute.isBlank()) {
            return "";
        }
        String digest = ZaloSignatureUtils.sha256Hex(salt + "|" + attribute.trim().toLowerCase());
        return digest.substring(0, Math.min(TOKEN_WIDTH, digest.length()));
    }

    /**
     * Fraction of fingerprint components that agree. Returns 0 when either side is
     * unusable or the attribute counts differ, so a changed client build is never
     * silently matched to an old device.
     */
    public static double similarity(String tokenA, String tokenB) {
        if (tokenA == null || tokenB == null || tokenA.isBlank() || tokenB.isBlank()) {
            return 0d;
        }
        String[] partsA = tokenA.split(":", -1);
        String[] partsB = tokenB.split(":", -1);
        if (partsA.length != partsB.length || partsA.length == 0) {
            return 0d;
        }
        int matched = 0;
        for (int i = 0; i < partsA.length; i++) {
            if (!partsA[i].isEmpty() && partsA[i].equals(partsB[i])) {
                matched++;
            }
        }
        return (double) matched / partsA.length;
    }

    /**
     * Irreversible but comparable form of a visitor IP, stored instead of the raw
     * address.
     */
    public static String hashIp(String ip, String salt) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        String digest = ZaloSignatureUtils.sha256Hex(salt + "|ip|" + ip.trim());
        return digest.substring(0, 32);
    }

    /**
     * Resolves the real visitor address. nginx sets {@code X-Real-IP} from
     * $remote_addr and overwrites any client-supplied value, so it is the only
     * signal that survives the same-host proxy chain; the socket address seen by
     * the app is always 127.0.0.1 and must never be used.
     */
    public static String resolveClientIp(String xRealIp, String xForwardedFor) {
        if (xRealIp != null && !xRealIp.isBlank() && !"unknown".equalsIgnoreCase(xRealIp.trim())) {
            return xRealIp.trim();
        }
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            String first = xForwardedFor.split(",")[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        return null;
    }
}
