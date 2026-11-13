package com.qrpublic.apartment.controller;

import com.qrpublic.apartment.model.SellerDTO;
import com.qrpublic.apartment.constant.HeaderConstant;
import com.qrpublic.apartment.model.notification.SellerEnvironmentListResponse;
import com.qrpublic.apartment.model.notification.SellerListResponse;
import com.qrpublic.apartment.paymentplan.request.CreateSellerPlanRequest;
import com.qrpublic.apartment.paymentplan.response.CreateSellerPlanResponse;
import com.qrpublic.apartment.requestmodel.Pagination;
import com.qrpublic.apartment.requestmodel.RequestDTO;
import com.qrpublic.apartment.requestmodel.SaleEnvDTO;
import com.qrpublic.apartment.requestmodel.SendNotificationRequest;
import com.qrpublic.apartment.saleenv.SaleEnvironmentService;
import com.qrpublic.apartment.saleenv.request.ListEnvironmentRequest;
import com.qrpublic.apartment.saleenv.response.ListEnvironmentResponse;
import com.qrpublic.apartment.service.AdminService;
import com.qrpublic.apartment.service.NotificationService;
import com.qrpublic.apartment.service.PaymentPlanAdminService;
import com.qrpublic.apartment.service.RequestService;
import com.qrpublic.apartment.service.YogaAdminService;
import com.qrpublic.apartment.template.ResponseEntityConvertor;
import com.qrpublic.apartment.user.request.ListUserRequest;
import com.qrpublic.apartment.user.response.ListUserResponse;
import com.qrpublic.apartment.yoga.request.ResetPasswordRequest;
import com.qrpublic.apartment.yoga.response.ResetPasswordResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/admin/v1")
public class AdminController {
        @Autowired
        RequestService requestService;

        @Autowired
        SaleEnvironmentService envService;

        @Autowired
        AdminService adminService;

        @Autowired
        NotificationService notificationService;

        @Autowired
        PaymentPlanAdminService paymentPlanAdminService;

        @Autowired
        YogaAdminService yogaAdminService;

        /**
         * Display environment list for administrator to manage, including the link
         * generation
         * TODO
         *
         * @return
         */
        @PostMapping("/getEnvironments")
        public Mono<ResponseEntity<ListEnvironmentResponse>> getEnvironments(
                        @RequestBody Pagination<ListEnvironmentRequest> request) {
                return adminService.getSaleEnvironment(request)
                                .map(ResponseEntity::ok)
                                .defaultIfEmpty(ResponseEntity.status(HttpStatus.NO_CONTENT).build());
        }

        /**
         * Administrator create request, product and response access generation page Not
         * used
         */
        @PostMapping("/generationPage")
        // @PreAuthorize(value = "hasRole('Admin')")
        public ResponseEntity<RequestDTO> getGenerationPage(@RequestBody SellerDTO sellerDTO) {
                // create request
                RequestDTO requestDTO = requestService.createTempRequestDTO(sellerDTO);

                // response, UI navigate to the link generation page
                return ResponseEntity.ok(requestDTO);
        }

        @PostMapping("/listUser")
        public Mono<ResponseEntity<ListUserResponse>> getListUser(@RequestBody Pagination<ListUserRequest> request) {
                return adminService.listInnerUsers(request)
                                .map(ResponseEntity::ok)
                                .defaultIfEmpty(ResponseEntity.status(HttpStatus.NO_CONTENT).build());
        }

        /**
         * Get detailed environment info including pricing records for a given request
         * UUID.
         */
        @GetMapping("/getEnvironmentDetail")
        public Mono<ResponseEntity<SaleEnvDTO>> getEnvironmentDetail(@RequestParam String requestUuid) {
                return envService.getEnvironmentDetailByRequestUuid(requestUuid)
                                .map(ResponseEntity::ok)
                                .defaultIfEmpty(ResponseEntity.notFound().build());
        }

        /**
         * Send notification to sellers.
         */
        @PostMapping("/notify/send")
        public Mono<ResponseEntity<Void>> sendNotification(
                        @RequestBody SendNotificationRequest request,
                        @RequestHeader(value = "X-User-ID", defaultValue = "admin") String adminUsername) {
                return ResponseEntityConvertor.convertToMonoResponseEntity(
                                notificationService.sendNotification(request, adminUsername));
        }

        /**
         * Get paginated list of sellers with environment counts (for notification
         * compose UI).
         */
        @GetMapping("/notify/sellers")
        public Mono<ResponseEntity<SellerListResponse>> getSellers(
                        @RequestParam(required = false) String search,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size) {
                return ResponseEntityConvertor.convertToMonoResponseEntity(
                                notificationService.getSellers(search, page, size));
        }

        /**
         * Get environments for a specific seller (on-demand loading).
         */
        @GetMapping("/notify/sellers/{username}/environments")
        public Mono<ResponseEntity<SellerEnvironmentListResponse>> getSellerEnvironments(
                        @PathVariable String username) {
                return ResponseEntityConvertor.convertToMonoResponseEntity(
                                notificationService.getSellerEnvironments(username));
        }

        /**
         * Extend the seller authentication link by generating a new token with +15
         * minutes expiry.
         */
        @PostMapping("/extendAuthLink")
        public Mono<ResponseEntity<SaleEnvDTO>> extendAuthLink(@RequestBody Map<String, String> body) {
                String requestUuid = body.get("requestUuid");
                return envService.extendAuthLink(requestUuid)
                                .map(ResponseEntity::ok)
                                .defaultIfEmpty(ResponseEntity.notFound().build());
        }

        /**
         * Assign a payment plan to a seller and get back the payment URL that the
         * admin sends to the seller manually. Repurposed from the old pricing-plan
         * flow: it now creates a payment record (CREATE) plus a payment_plan_user
         * assignment.
         */
        @PostMapping("/createSellerPlan")
        public Mono<ResponseEntity<CreateSellerPlanResponse>> createSellerPlan(
                        @Valid @RequestBody CreateSellerPlanRequest request,
                        @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "admin") String adminUsername) {
                return Mono.fromCallable(() -> paymentPlanAdminService.createSellerPlan(request, adminUsername))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
        }

        /**
         * Admin trigger reset-password from the User List, for any username
         * (buyer or seller). Invalidates the current password and returns a
         * fresh single-use setup link to forward.
         */
        @PostMapping("/user/resetPassword")
        public Mono<ResponseEntity<ResetPasswordResponse>> resetPassword(
                        @RequestBody ResetPasswordRequest request,
                        @RequestHeader(value = HeaderConstant.USER_ID_HEADER, defaultValue = "admin") String adminUsername) {
                return Mono.fromCallable(() -> yogaAdminService.resetPassword(request, adminUsername))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(ResponseEntityConvertor::convertToMonoResponseEntity);
        }
}
