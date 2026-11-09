package com.qrpublic.apartment.integration.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Request body of the Zalo OA "Tin Tư vấn" message API
 * (POST https://openapi.zalo.me/v3.0/oa/message/cs).
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ZaloSendTextRequest {

    private Recipient recipient;

    private Message message;

    public static ZaloSendTextRequest of(String userId, String text, String quoteMsgId) {
        ZaloSendTextRequest request = new ZaloSendTextRequest();

        Recipient recipient = new Recipient();
        recipient.setUserId(userId);
        request.setRecipient(recipient);

        Message message = new Message();
        message.setText(text);
        message.setQuoteMsgId(quoteMsgId);
        request.setMessage(message);

        return request;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Recipient {
        @JsonProperty("user_id")
        private String userId;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Message {
        @JsonProperty("text")
        private String text;

        /**
         * Optional: quote an existing Zalo message so the administrator sees which
         * customer item they are answering. Best effort — Zalo ignores unknown fields.
         */
        @JsonProperty("quote_msg_id")
        private String quoteMsgId;
    }
}
