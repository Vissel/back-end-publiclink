package com.qrpublic.apartment.chat.integration.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Response of the Zalo OA message API. Zalo answers with
 * {@code {"data": {...}, "error": 0, "message": "Success"}}.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ZaloSendResponse {

    private ZaloSendData data;

    private Integer error;

    private String message;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ZaloSendData {

        @JsonProperty("message_id")
        private String messageId;

        @JsonProperty("user_id")
        private String userId;

        @JsonProperty("sent_time")
        private Long sentTime;
    }
}
