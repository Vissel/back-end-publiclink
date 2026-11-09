package com.qrpublic.apartment.chat.integration.model;

import com.qrpublic.apartment.chat.exception.ApplicationException;

/**
 * A Zalo API call completed at HTTP level but Zalo refused the operation
 * (non-zero {@code error}/{@code error_code} in the JSON body).
 *
 * The original Zalo code is kept in {@link #getZaloCode()} so it can be stored
 * on the chat message and read by support staff — codes such as -204 (expired
 * token) or -213 (user does not follow the OA) are the only clue available when
 * a message is stuck.
 */
public class ZaloApiException extends ApplicationException {

    private final int zaloCode;

    public ZaloApiException(String message, int zaloCode) {
        super(message, 502);
        this.zaloCode = zaloCode;
    }

    public int getZaloCode() {
        return zaloCode;
    }

    public static ZaloApiException fromSend(int zaloCode, String message) {
        return new ZaloApiException("Zalo OA send refused [" + zaloCode + "]: " + message, zaloCode);
    }

    public static ZaloApiException fromToken(int zaloCode, String message) {
        return new ZaloApiException("Zalo OAuth refused [" + zaloCode + "]: " + message, zaloCode);
    }
}
