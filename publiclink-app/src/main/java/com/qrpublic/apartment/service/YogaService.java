package com.qrpublic.apartment.service;

import com.qrpublic.apartment.core.model.YogaBookingModel;
import com.qrpublic.apartment.core.model.YogaOccurrenceModel;
import com.qrpublic.apartment.domain.buyerplan.PasswordSetupDomain;
import com.qrpublic.apartment.domain.yoga.YogaBookingDomain;
import com.qrpublic.apartment.domain.yoga.YogaEnvAccess;
import com.qrpublic.apartment.template.model.Result;
import com.qrpublic.apartment.template.service.ProcessCallback;
import com.qrpublic.apartment.template.service.PublicLinkServiceTemplate;
import com.qrpublic.apartment.yoga.request.BuyerSetupRequest;
import com.qrpublic.apartment.yoga.request.YogaBookRequest;
import com.qrpublic.apartment.yoga.request.YogaCalendarRequest;
import com.qrpublic.apartment.yoga.request.YogaCancelRequest;
import com.qrpublic.apartment.yoga.request.YogaRangeRequest;
import com.qrpublic.apartment.yoga.response.BuyerSetupInfoResponse;
import com.qrpublic.apartment.yoga.response.YogaOccurrencesResponse;
import com.qrpublic.apartment.yoga.response.YogaResultResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Public + buyer-facing yoga operations (the only layer running the service
 * template). The gateway-forwarded X-User-ID (may be blank for anonymous
 * visitors) decides the seller-vs-buyer view and the plan identity inside the
 * domain.
 */
@Slf4j
@Service
public class YogaService {

    @Autowired
    private PublicLinkServiceTemplate publicLinkServiceTemplate;

    @Autowired
    private YogaBookingDomain yogaBookingDomain;

    @Autowired
    private YogaEnvAccess yogaEnvAccess;

    @Autowired
    private PasswordSetupDomain passwordSetupDomain;

    public Result<YogaOccurrencesResponse> calendar(YogaCalendarRequest request, String callerUsername) {
        return publicLinkServiceTemplate
                .execute(new ProcessCallback<YogaCalendarRequest, YogaOccurrencesResponse>() {
                    @Override
                    public YogaCalendarRequest getRequest() {
                        return request;
                    }

                    @Override
                    public void preProcess(YogaCalendarRequest req) {
                        Assert.hasText(req.getToken(), "Link token is required.");
                    }

                    @Override
                    public YogaOccurrencesResponse process() {
                        LocalDate weekStart = parseDateOrThisMonday(request.getWeekStart());
                        List<YogaOccurrenceModel> items = yogaBookingDomain.calendar(request.getToken(), weekStart,
                                callerUsername);
                        boolean sellerView = isSeller(request.getToken(), callerUsername);
                        YogaOccurrencesResponse response = YogaOccurrencesResponse.of(items, sellerView);
                        attachEnvMeta(response, request.getToken());
                        return response;
                    }
                });
    }

    public Result<YogaOccurrencesResponse> classes(YogaRangeRequest request, String callerUsername) {
        return publicLinkServiceTemplate
                .execute(new ProcessCallback<YogaRangeRequest, YogaOccurrencesResponse>() {
                    @Override
                    public YogaRangeRequest getRequest() {
                        return request;
                    }

                    @Override
                    public void preProcess(YogaRangeRequest req) {
                        Assert.hasText(req.getToken(), "Link token is required.");
                    }

                    @Override
                    public YogaOccurrencesResponse process() {
                        List<YogaOccurrenceModel> items = yogaBookingDomain.listOccurrences(
                                request.getToken(), parseDate(request.getFromDate()), parseDate(request.getToDate()),
                                callerUsername);
                        boolean sellerView = isSeller(request.getToken(), callerUsername);
                        YogaOccurrencesResponse response = YogaOccurrencesResponse.of(items, sellerView);
                        attachEnvMeta(response, request.getToken());
                        return response;
                    }
                });
    }

    public Result<YogaResultResponse<YogaBookingModel>> book(YogaBookRequest request, String callerUsername) {
        return publicLinkServiceTemplate.execute(
                new ProcessCallback<YogaBookRequest, YogaResultResponse<YogaBookingModel>>() {
                    @Override
                    public YogaBookRequest getRequest() {
                        return request;
                    }

                    @Override
                    public void preProcess(YogaBookRequest req) {
                        Assert.hasText(req.getToken(), "Link token is required.");
                        Assert.hasText(req.getClassCode(), "A class must be selected.");
                        Assert.hasText(req.getDate(), "A booking date is required.");
                    }

                    @Override
                    public YogaResultResponse<YogaBookingModel> process() {
                        boolean sellerView = isSeller(request.getToken(), callerUsername);
                        YogaBookingModel booking = yogaBookingDomain.book(request.getToken(),
                                request.getClassCode(), parseDate(request.getDate()), request.getBuyerName(),
                                request.getBuyerUsername(), request.getPaymentMode(), callerUsername, sellerView);
                        return YogaResultResponse.of(booking);
                    }
                });
    }

    public Result<YogaResultResponse<YogaBookingModel>> cancel(YogaCancelRequest request, String callerUsername) {
        return publicLinkServiceTemplate.execute(
                new ProcessCallback<YogaCancelRequest, YogaResultResponse<YogaBookingModel>>() {
                    @Override
                    public YogaCancelRequest getRequest() {
                        return request;
                    }

                    @Override
                    public void preProcess(YogaCancelRequest req) {
                        Assert.hasText(req.getBookingCode(), "Booking code is required.");
                    }

                    @Override
                    public YogaResultResponse<YogaBookingModel> process() {
                        boolean sellerView = isSeller(request.getToken(), callerUsername);
                        return YogaResultResponse.of(yogaBookingDomain.cancel(
                                request.getToken(), request.getBookingCode(), callerUsername, sellerView));
                    }
                });
    }

    public Result<BuyerSetupInfoResponse> setupInfo(BuyerSetupRequest request) {
        return publicLinkServiceTemplate
                .execute(new ProcessCallback<BuyerSetupRequest, BuyerSetupInfoResponse>() {
                    @Override
                    public BuyerSetupRequest getRequest() {
                        return request;
                    }

                    @Override
                    public void preProcess(BuyerSetupRequest req) {
                        Assert.hasText(req.getToken(), "Setup token is required.");
                    }

                    @Override
                    public BuyerSetupInfoResponse process() {
                        PasswordSetupDomain.SetupInfo info = passwordSetupDomain.info(request.getToken());
                        BuyerSetupInfoResponse response = new BuyerSetupInfoResponse();
                        response.setUsername(info.getUsername());
                        response.setFullName(info.getFullName());
                        response.setPurpose(info.getPurpose());
                        response.setValid(info.isValid());
                        response.setExpiresAt(info.getExpiresAt());
                        return response;
                    }
                });
    }

    public Result<Void> setupComplete(BuyerSetupRequest request) {
        return publicLinkServiceTemplate.execute(new ProcessCallback<BuyerSetupRequest, Void>() {
            @Override
            public BuyerSetupRequest getRequest() {
                return request;
            }

            @Override
            public void preProcess(BuyerSetupRequest req) {
                Assert.hasText(req.getToken(), "Setup token is required.");
                Assert.hasText(req.getPassword(), "A new password is required.");
            }

            @Override
            public Void process() {
                passwordSetupDomain.complete(request.getToken(), request.getPassword());
                return null;
            }
        });
    }

    private boolean isSeller(String token, String callerUsername) {
        try {
            return callerUsername != null && !callerUsername.isBlank()
                    && callerUsername.equals(yogaEnvAccess.resolveForRead(token).getSellerUsername());
        } catch (Exception e) {
            return false;
        }
    }

    private void attachEnvMeta(YogaOccurrencesResponse response, String token) {
        try {
            var env = yogaEnvAccess.resolveForRead(token);
            response.setProductName(env.getProductName());
            response.setDropInPrice(env.getDropInPrice());
        } catch (Exception ignored) {
            // meta is best-effort; the occurrences already carry the essentials
        }
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

    private LocalDate parseDateOrThisMonday(String value) {
        LocalDate parsed = parseDate(value);
        LocalDate base = parsed != null ? parsed : yogaEnvAccess.dbToday();
        return base.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
    }
}
