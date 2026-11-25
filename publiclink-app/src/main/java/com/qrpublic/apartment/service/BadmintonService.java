package com.qrpublic.apartment.service;

import com.qrpublic.apartment.badminton.request.BadmintonBookRequest;
import com.qrpublic.apartment.badminton.request.BadmintonBookingCodeRequest;
import com.qrpublic.apartment.badminton.request.BadmintonGridRequest;
import com.qrpublic.apartment.badminton.request.BadmintonTokenRequest;
import com.qrpublic.apartment.badminton.response.BadmintonGridResponse;
import com.qrpublic.apartment.core.model.BadmintonBookingModel;
import com.qrpublic.apartment.domain.badminton.BadmintonBookingDomain;
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

/** Public badminton API operations wrapped in the standard service template. */
@Slf4j
@Service
public class BadmintonService {
    @Autowired
    private PublicLinkServiceTemplate publicLinkServiceTemplate;

    @Autowired
    private BadmintonBookingDomain badmintonBookingDomain;

    public Result<BadmintonGridResponse> grid(BadmintonGridRequest request, String callerUsername) {
        return publicLinkServiceTemplate.execute(new ProcessCallback<BadmintonGridRequest, BadmintonGridResponse>() {
            @Override
            public BadmintonGridRequest getRequest() {
                return request;
            }

            @Override
            public void preProcess(BadmintonGridRequest req) {
                Assert.hasText(req.getToken(), "Link token is required.");
                Assert.hasText(req.getDate(), "A grid date is required.");
            }

            @Override
            public BadmintonGridResponse process() {
                return badmintonBookingDomain.grid(request.getToken(), parseDate(request.getDate()), callerUsername);
            }
        });
    }

    public Result<BadmintonBookingModel> book(BadmintonBookRequest request, String callerUsername) {
        return publicLinkServiceTemplate.execute(new ProcessCallback<BadmintonBookRequest, BadmintonBookingModel>() {
            @Override
            public BadmintonBookRequest getRequest() {
                return request;
            }

            @Override
            public void preProcess(BadmintonBookRequest req) {
                Assert.hasText(req.getToken(), "Link token is required.");
                Assert.hasText(req.getCourtCode(), "A court must be selected.");
                Assert.hasText(req.getDate(), "A booking date is required.");
                Assert.hasText(req.getStartTime(), "A start time is required.");
            }

            @Override
            public BadmintonBookingModel process() {
                return badmintonBookingDomain.book(request.getToken(), request.getCourtCode(),
                        parseDate(request.getDate()), request.getStartTime(), callerUsername);
            }
        });
    }

    public Result<BadmintonBookingModel> paymentConfirm(BadmintonBookingCodeRequest request,
            String callerUsername) {
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
                    }

                    @Override
                    public BadmintonBookingModel process() {
                        return badmintonBookingDomain.paymentConfirm(request.getToken(),
                                request.getBookingCode(), callerUsername);
                    }
                });
    }

    public Result<BadmintonBookingModel> cancel(BadmintonBookingCodeRequest request, String callerUsername) {
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
                    }

                    @Override
                    public BadmintonBookingModel process() {
                        return badmintonBookingDomain.cancel(request.getToken(),
                                request.getBookingCode(), callerUsername);
                    }
                });
    }

    public Result<List<BadmintonBookingModel>> myBookings(BadmintonTokenRequest request, String callerUsername) {
        return publicLinkServiceTemplate.execute(
                new ProcessCallback<BadmintonTokenRequest, List<BadmintonBookingModel>>() {
                    @Override
                    public BadmintonTokenRequest getRequest() {
                        return request;
                    }

                    @Override
                    public void preProcess(BadmintonTokenRequest req) {
                        Assert.hasText(req.getToken(), "Link token is required.");
                    }

                    @Override
                    public List<BadmintonBookingModel> process() {
                        return badmintonBookingDomain.myBookings(request.getToken(), callerUsername);
                    }
                });
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
