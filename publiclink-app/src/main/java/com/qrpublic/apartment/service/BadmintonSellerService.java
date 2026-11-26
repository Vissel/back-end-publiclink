package com.qrpublic.apartment.service;

import com.qrpublic.apartment.badminton.request.BadmintonBookingCodeRequest;
import com.qrpublic.apartment.badminton.request.BadmintonCourtDeleteRequest;
import com.qrpublic.apartment.badminton.request.BadmintonCourtSaveRequest;
import com.qrpublic.apartment.badminton.request.BadmintonSellerBookingsRequest;
import com.qrpublic.apartment.badminton.request.BadmintonSellerCourtsRequest;
import com.qrpublic.apartment.badminton.request.BadmintonSellerRejectRequest;
import com.qrpublic.apartment.badminton.response.BadmintonCourtResponse;
import com.qrpublic.apartment.badminton.response.BadmintonCourtScheduleResponse;
import com.qrpublic.apartment.core.model.BadmintonBookingModel;
import com.qrpublic.apartment.core.model.BadmintonCourtModel;
import com.qrpublic.apartment.core.model.BadmintonCourtScheduleModel;
import com.qrpublic.apartment.domain.badminton.BadmintonBookingDomain;
import com.qrpublic.apartment.domain.badminton.BadmintonCourtDomain;
import com.qrpublic.apartment.template.model.Result;
import com.qrpublic.apartment.template.service.ProcessCallback;
import com.qrpublic.apartment.template.service.PublicLinkServiceTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/** Seller badminton management; identity always comes from the JWT header. */
@Slf4j
@Service
public class BadmintonSellerService {
    @Autowired
    private PublicLinkServiceTemplate publicLinkServiceTemplate;

    @Autowired
    private BadmintonCourtDomain badmintonCourtDomain;

    @Autowired
    private BadmintonBookingDomain badmintonBookingDomain;

    public Result<List<BadmintonCourtResponse>> courts(BadmintonSellerCourtsRequest request,
            String sellerUsername) {
        return publicLinkServiceTemplate.execute(
                new ProcessCallback<BadmintonSellerCourtsRequest, List<BadmintonCourtResponse>>() {
                    @Override
                    public BadmintonSellerCourtsRequest getRequest() {
                        return request;
                    }

                    @Override
                    public void preProcess(BadmintonSellerCourtsRequest req) {
                        Assert.hasText(req.getToken(), "Link token is required.");
                        Assert.hasText(sellerUsername, "Seller identity is required.");
                    }

                    @Override
                    public List<BadmintonCourtResponse> process() {
                        return badmintonCourtDomain.listCourts(request.getToken(), sellerUsername).stream()
                                .map(BadmintonSellerService.this::toResponse)
                                .toList();
                    }
                });
    }

    public Result<BadmintonCourtResponse> saveCourt(BadmintonCourtSaveRequest request, String sellerUsername) {
        return publicLinkServiceTemplate.execute(
                new ProcessCallback<BadmintonCourtSaveRequest, BadmintonCourtResponse>() {
                    @Override
                    public BadmintonCourtSaveRequest getRequest() {
                        return request;
                    }

                    @Override
                    public void preProcess(BadmintonCourtSaveRequest req) {
                        Assert.hasText(req.getToken(), "Link token is required.");
                        Assert.hasText(sellerUsername, "Seller identity is required.");
                        Assert.hasText(req.getCourtName(), "Court name is required.");
                    }

                    @Override
                    public BadmintonCourtResponse process() {
                        List<BadmintonCourtScheduleModel> schedules = request.getSchedules() == null ? null
                                : request.getSchedules().stream().map(s -> BadmintonCourtScheduleModel.builder()
                                        .dayOfWeek(s.getDayOfWeek())
                                        .openTime(s.getOpenTime())
                                        .closeTime(s.getCloseTime())
                                        .slotMinutes(s.getSlotMinutes())
                                        .priceVnd(s.getPriceVnd())
                                        .build()).toList();
                        BadmintonCourtModel draft = BadmintonCourtModel.builder()
                                .courtCode(request.getCourtCode())
                                .courtName(request.getCourtName())
                                .schedules(schedules)
                                .build();
                        return toResponse(badmintonCourtDomain.saveCourt(
                                request.getToken(), sellerUsername, draft));
                    }
                });
    }

    public Result<Void> deleteCourt(BadmintonCourtDeleteRequest request, String sellerUsername) {
        return publicLinkServiceTemplate.execute(new ProcessCallback<BadmintonCourtDeleteRequest, Void>() {
            @Override
            public BadmintonCourtDeleteRequest getRequest() {
                return request;
            }

            @Override
            public void preProcess(BadmintonCourtDeleteRequest req) {
                Assert.hasText(req.getToken(), "Link token is required.");
                Assert.hasText(req.getCourtCode(), "Court code is required.");
                Assert.hasText(sellerUsername, "Seller identity is required.");
            }

            @Override
            public Void process() {
                badmintonCourtDomain.deleteCourt(request.getToken(), sellerUsername, request.getCourtCode());
                return null;
            }
        });
    }

    public Result<List<BadmintonBookingModel>> bookings(BadmintonSellerBookingsRequest request,
            String sellerUsername) {
        return publicLinkServiceTemplate.execute(
                new ProcessCallback<BadmintonSellerBookingsRequest, List<BadmintonBookingModel>>() {
                    @Override
                    public BadmintonSellerBookingsRequest getRequest() {
                        return request;
                    }

                    @Override
                    public void preProcess(BadmintonSellerBookingsRequest req) {
                        Assert.hasText(req.getToken(), "Link token is required.");
                        Assert.hasText(sellerUsername, "Seller identity is required.");
                    }

                    @Override
                    public List<BadmintonBookingModel> process() {
                        return badmintonBookingDomain.sellerBookings(request.getToken(), sellerUsername,
                                parseDate(request.getDate()));
                    }
                });
    }

    public Result<BadmintonBookingModel> confirm(BadmintonBookingCodeRequest request, String sellerUsername) {
        return publicLinkServiceTemplate.execute(
                new ProcessCallback<BadmintonBookingCodeRequest, BadmintonBookingModel>() {
                    @Override
                    public BadmintonBookingCodeRequest getRequest() {
                        return request;
                    }

                    @Override
                    public void preProcess(BadmintonBookingCodeRequest req) {
                        Assert.hasText(req.getToken(), "Link token is required.");
                        Assert.hasText(req.getBookingCode(), "Booking code is required.");
                        Assert.hasText(sellerUsername, "Seller identity is required.");
                    }

                    @Override
                    public BadmintonBookingModel process() {
                        return badmintonBookingDomain.confirm(request.getToken(), sellerUsername,
                                request.getBookingCode());
                    }
                });
    }

    public Result<BadmintonBookingModel> reject(BadmintonSellerRejectRequest request, String sellerUsername) {
        return publicLinkServiceTemplate.execute(
                new ProcessCallback<BadmintonSellerRejectRequest, BadmintonBookingModel>() {
                    @Override
                    public BadmintonSellerRejectRequest getRequest() {
                        return request;
                    }

                    @Override
                    public void preProcess(BadmintonSellerRejectRequest req) {
                        Assert.hasText(req.getToken(), "Link token is required.");
                        Assert.hasText(req.getBookingCode(), "Booking code is required.");
                        Assert.hasText(sellerUsername, "Seller identity is required.");
                    }

                    @Override
                    public BadmintonBookingModel process() {
                        return badmintonBookingDomain.reject(request.getToken(), sellerUsername,
                                request.getBookingCode(), request.getNote());
                    }
                });
    }

    private BadmintonCourtResponse toResponse(BadmintonCourtModel model) {
        List<BadmintonCourtScheduleResponse> schedules = model.getSchedules() == null ? List.of()
                : model.getSchedules().stream().map(s -> BadmintonCourtScheduleResponse.builder()
                        .dayOfWeek(s.getDayOfWeek())
                        .openTime(s.getOpenTime())
                        .closeTime(s.getCloseTime())
                        .slotMinutes(s.getSlotMinutes())
                        .priceVnd(s.getPriceVnd())
                        .build()).toList();
        return BadmintonCourtResponse.builder()
                .courtCode(model.getCourtCode())
                .courtName(model.getCourtName())
                .active(Boolean.TRUE.equals(model.getActive()))
                .schedules(schedules)
                .build();
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim().substring(0, Math.min(10, value.trim().length())));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date: " + value);
        }
    }
}
