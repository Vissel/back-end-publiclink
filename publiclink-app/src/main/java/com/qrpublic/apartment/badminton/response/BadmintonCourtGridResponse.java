package com.qrpublic.apartment.badminton.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** One court column in the public grid. */
@Data
@Builder
public class BadmintonCourtGridResponse {
    private String courtCode;
    private String courtName;
    private boolean active;
    private List<BadmintonFrameResponse> frames;
}
