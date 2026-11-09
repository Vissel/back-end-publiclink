package com.qrpublic.apartment.integration;

import com.qrpublic.apartment.integration.model.ZaloApiException;
import com.qrpublic.apartment.integration.model.ZaloSendResponse;
import com.qrpublic.apartment.integration.model.ZaloSendTextRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Reactive client for the Zalo Official Account OpenAPI.
 *
 * Only the message API is needed by the chat bridge; the OA authenticates every
 * call with the {@code access_token} header obtained through {@link ZaloAuthClient}.
 */
@Slf4j
@Component
public class ZaloOaClient {

    private static final String TEXT_MESSAGE_PATH = "/v3.0/oa/message/cs";

    private final WebClient webClient;

    public ZaloOaClient(@Value("${zalo.openapi.base-url:https://openapi.zalo.me}") String baseUrl) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    /**
     * Send a consultative text message ("Tin Tư vấn") from the OA to one Zalo user.
     *
     * @param accessToken OA access token
     * @param request     recipient + text payload
     * @return the Zalo response carrying the message id used for status matching
     */
    public Mono<ZaloSendResponse> sendText(String accessToken, ZaloSendTextRequest request) {
        return webClient.post()
                .uri(TEXT_MESSAGE_PATH)
                .header("access_token", accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ZaloSendResponse.class)
                .flatMap(this::checkSendResult);
    }

    private Mono<ZaloSendResponse> checkSendResult(ZaloSendResponse response) {
        if (response == null) {
            return Mono.error(new ZaloApiException("Empty response from Zalo OA message API", -1));
        }
        Integer error = response.getError();
        if (error != null && error != 0) {
            return Mono.error(ZaloApiException.fromSend(error, response.getMessage()));
        }
        if (response.getData() == null || response.getData().getMessageId() == null
                || response.getData().getMessageId().isBlank()) {
            return Mono.error(new ZaloApiException("Zalo OA accepted the message but returned no message id", -1));
        }
        return Mono.just(response);
    }
}
