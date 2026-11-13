package com.qrpublic.apartment.service;

import com.qrpublic.apartment.core.model.YogaBookingModel;
import com.qrpublic.apartment.core.model.YogaClassModel;
import com.qrpublic.apartment.core.model.SellerPlanModel;
import com.qrpublic.apartment.core.model.YogaEnvModel;
import com.qrpublic.apartment.core.service.CoreYogaEnvironmentService;
import com.qrpublic.apartment.domain.buyerplan.SellerPaymentPlanDomain;
import com.qrpublic.apartment.domain.yoga.YogaBookingDomain;
import com.qrpublic.apartment.domain.yoga.YogaClassDomain;
import com.qrpublic.apartment.template.model.Result;
import com.qrpublic.apartment.template.service.ProcessCallback;
import com.qrpublic.apartment.template.service.PublicLinkServiceTemplate;
import com.qrpublic.apartment.yoga.request.SellerPlanUpsertRequest;
import com.qrpublic.apartment.yoga.request.YogaBookingToggleRequest;
import com.qrpublic.apartment.yoga.request.YogaClassUpsertRequest;
import com.qrpublic.apartment.yoga.request.YogaRangeRequest;
import com.qrpublic.apartment.yoga.response.YogaResultResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.time.LocalDate;
import java.util.List;

/**
 * Seller-facing yoga management. The caller is always taken from the
 * gateway-forwarded header; the domain asserts environment ownership, so a
 * seller can never edit another seller's page.
 */
@Slf4j
@Service
public class YogaSellerService {

    @Autowired
    private PublicLinkServiceTemplate publicLinkServiceTemplate;

    @Autowired
    private YogaClassDomain yogaClassDomain;

    @Autowired
    private YogaBookingDomain yogaBookingDomain;

    @Autowired
    private SellerPaymentPlanDomain sellerPaymentPlanDomain;

    @Autowired
    private CoreYogaEnvironmentService coreYogaEnvironmentService;

    public Result<List<YogaClassModel>> listClasses(String token, String sellerUsername) {
        return publicLinkServiceTemplate.execute(new ProcessCallback<String, List<YogaClassModel>>() {
            @Override
            public String getRequest() {
                return token;
            }

            @Override
            public void preProcess(String req) {
                Assert.hasText(req, "Link token is required.");
                Assert.hasText(sellerUsername, "Seller identity is required.");
            }

            @Override
            public List<YogaClassModel> process() {
                return yogaClassDomain.listClasses(token, sellerUsername);
            }
        });
    }

    public Result<YogaResultResponse<YogaClassModel>> upsertClass(YogaClassUpsertRequest request,
            String sellerUsername) {
        return publicLinkServiceTemplate.execute(
                new ProcessCallback<YogaClassUpsertRequest, YogaResultResponse<YogaClassModel>>() {
                    @Override
                    public YogaClassUpsertRequest getRequest() {
                        return request;
                    }

                    @Override
                    public void preProcess(YogaClassUpsertRequest req) {
                        Assert.hasText(req.getToken(), "Link token is required.");
                        Assert.hasText(sellerUsername, "Seller identity is required.");
                        Assert.hasText(req.getTitle(), "Class title is required.");
                    }

                    @Override
                    public YogaResultResponse<YogaClassModel> process() {
                        YogaClassModel draft = YogaClassModel.builder()
                                .dayOfWeek(req0(request.getDayOfWeek()))
                                .startTime(request.getStartTime())
                                .endTime(request.getEndTime())
                                .title(request.getTitle().trim())
                                .coach(request.getCoach() == null ? null : request.getCoach().trim())
                                .capacity(request.getCapacity() == null ? 1 : request.getCapacity())
                                .active(request.getActive())
                                .build();
                        YogaClassModel saved = request.getClassCode() == null || request.getClassCode().isBlank()
                                ? yogaClassDomain.createClass(request.getToken(), sellerUsername, draft)
                                : yogaClassDomain.updateClass(request.getToken(), sellerUsername,
                                        request.getClassCode(), draft);
                        return YogaResultResponse.of(saved);
                    }
                });
    }

    public Result<Void> deleteClass(String token, String classCode, String sellerUsername) {
        return publicLinkServiceTemplate.execute(new ProcessCallback<Void, Void>() {
            @Override
            public Void getRequest() {
                return null;
            }

            @Override
            public void preProcess(Void req) {
                Assert.hasText(token, "Link token is required.");
                Assert.hasText(classCode, "Class code is required.");
            }

            @Override
            public Void process() {
                yogaClassDomain.deleteClass(token, classCode, sellerUsername);
                return null;
            }
        });
    }

    public Result<List<SellerPlanModel>> listPlans(String token, String sellerUsername) {
        return publicLinkServiceTemplate.execute(new ProcessCallback<String, List<SellerPlanModel>>() {
            @Override
            public String getRequest() {
                return token;
            }

            @Override
            public void preProcess(String req) {
                Assert.hasText(req, "Link token is required.");
                Assert.hasText(sellerUsername, "Seller identity is required.");
            }

            @Override
            public List<SellerPlanModel> process() {
                return sellerPaymentPlanDomain.listPlans(token, sellerUsername);
            }
        });
    }

    public Result<YogaResultResponse<SellerPlanModel>> createPlan(SellerPlanUpsertRequest request,
            String sellerUsername) {
        return publicLinkServiceTemplate.execute(
                new ProcessCallback<SellerPlanUpsertRequest, YogaResultResponse<SellerPlanModel>>() {
                    @Override
                    public SellerPlanUpsertRequest getRequest() {
                        return request;
                    }

                    @Override
                    public void preProcess(SellerPlanUpsertRequest req) {
                        Assert.hasText(req.getToken(), "Link token is required.");
                        Assert.hasText(sellerUsername, "Seller identity is required.");
                        Assert.hasText(req.getBuyerName(), "Buyer name is required.");
                    }

                    @Override
                    public YogaResultResponse<SellerPlanModel> process() {
                        SellerPlanModel plan = sellerPaymentPlanDomain.createPlan(request.getToken(),
                                sellerUsername, request.getBuyerName(), request.getBuyerEmail(),
                                request.getTotalDays() == null ? 0 : request.getTotalDays(),
                                request.getAmountVnd());
                        return YogaResultResponse.of(plan);
                    }
                });
    }

    public Result<YogaResultResponse<SellerPlanModel>> updatePlan(SellerPlanUpsertRequest request,
            String sellerUsername) {
        return publicLinkServiceTemplate.execute(
                new ProcessCallback<SellerPlanUpsertRequest, YogaResultResponse<SellerPlanModel>>() {
                    @Override
                    public SellerPlanUpsertRequest getRequest() {
                        return request;
                    }

                    @Override
                    public void preProcess(SellerPlanUpsertRequest req) {
                        Assert.hasText(req.getToken(), "Link token is required.");
                        Assert.hasText(req.getSellerPlanCode(), "Plan code is required.");
                    }

                    @Override
                    public YogaResultResponse<SellerPlanModel> process() {
                        SellerPlanModel plan = sellerPaymentPlanDomain.updatePlan(request.getToken(),
                                sellerUsername, request.getSellerPlanCode(), request.getTotalDays(),
                                request.getAmountVnd(), request.getBuyerEmail(), request.getState());
                        return YogaResultResponse.of(plan);
                    }
                });
    }

    public Result<YogaResultResponse<SellerPlanModel>> resendPlanLink(String token, String sellerPlanCode,
            String sellerUsername) {
        return publicLinkServiceTemplate.execute(
                new ProcessCallback<Void, YogaResultResponse<SellerPlanModel>>() {
                    @Override
                    public Void getRequest() {
                        return null;
                    }

                    @Override
                    public void preProcess(Void req) {
                        Assert.hasText(token, "Link token is required.");
                        Assert.hasText(sellerPlanCode, "Plan code is required.");
                    }

                    @Override
                    public YogaResultResponse<SellerPlanModel> process() {
                        return YogaResultResponse.of(
                                sellerPaymentPlanDomain.resendLink(token, sellerUsername, sellerPlanCode));
                    }
                });
    }

    public Result<List<YogaBookingModel>> listBookings(YogaRangeRequest request, String sellerUsername) {
        return publicLinkServiceTemplate.execute(new ProcessCallback<YogaRangeRequest, List<YogaBookingModel>>() {
            @Override
            public YogaRangeRequest getRequest() {
                return request;
            }

            @Override
            public void preProcess(YogaRangeRequest req) {
                Assert.hasText(req.getToken(), "Link token is required.");
                Assert.hasText(sellerUsername, "Seller identity is required.");
            }

            @Override
            public List<YogaBookingModel> process() {
                return yogaBookingDomain.sellerBookings(request.getToken(), sellerUsername,
                        parseDate(request.getFromDate()), parseDate(request.getToDate()));
            }
        });
    }

    public Result<YogaResultResponse<YogaBookingModel>> toggleBooking(YogaBookingToggleRequest request,
            String sellerUsername) {
        return publicLinkServiceTemplate.execute(
                new ProcessCallback<YogaBookingToggleRequest, YogaResultResponse<YogaBookingModel>>() {
                    @Override
                    public YogaBookingToggleRequest getRequest() {
                        return request;
                    }

                    @Override
                    public void preProcess(YogaBookingToggleRequest req) {
                        Assert.hasText(req.getToken(), "Link token is required.");
                        Assert.hasText(req.getBookingCode(), "Booking code is required.");
                    }

                    @Override
                    public YogaResultResponse<YogaBookingModel> process() {
                        return YogaResultResponse.of(yogaBookingDomain.toggleBooking(request.getToken(),
                                sellerUsername, request.getBookingCode(),
                                Boolean.TRUE.equals(request.getPaid()), Boolean.TRUE.equals(request.getAttended())));
                    }
                });
    }

    /** Yoga environments owned by the seller, for the setup-page selector. */
    public Result<List<YogaEnvModel>> myYogaEnvironments(String sellerUsername) {
        return publicLinkServiceTemplate.execute(new ProcessCallback<String, List<YogaEnvModel>>() {
            @Override
            public String getRequest() {
                return sellerUsername;
            }

            @Override
            public void preProcess(String req) {
                Assert.hasText(sellerUsername, "Seller identity is required.");
            }

            @Override
            public List<YogaEnvModel> process() {
                return coreYogaEnvironmentService.findYogaEnvsBySeller(sellerUsername);
            }
        });
    }

    private int req0(Integer value) {
        return value == null ? 0 : value;
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim().substring(0, Math.min(10, value.trim().length())));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid date: " + value);
        }
    }
}
