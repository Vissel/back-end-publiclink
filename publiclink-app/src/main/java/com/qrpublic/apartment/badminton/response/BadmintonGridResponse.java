package com.qrpublic.apartment.badminton.response;

import com.qrpublic.apartment.core.model.BadmintonBookingModel;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/** Exact public grid response contract. */
@Data
@Builder
public class BadmintonGridResponse {
    private String date;
    private List<BadmintonCourtGridResponse> courts;
    private boolean sellerView;
    private List<BadmintonBookingModel> myBookings;
}
