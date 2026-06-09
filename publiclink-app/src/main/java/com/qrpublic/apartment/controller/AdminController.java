package com.qrpublic.apartment.controller;

import com.qrpublic.apartment.model.SellerDTO;
import com.qrpublic.apartment.model.notification.SellerEnvironmentListResponse;
import com.qrpublic.apartment.model.notification.SellerListResponse;
import com.qrpublic.apartment.requestmodel.Pagination;
import com.qrpublic.apartment.requestmodel.RequestDTO;
import com.qrpublic.apartment.requestmodel.SaleEnvDTO;
import com.qrpublic.apartment.requestmodel.SendNotificationRequest;
import com.qrpublic.apartment.saleenv.SaleEnvironmentService;
import com.qrpublic.apartment.saleenv.request.ListEnvironmentRequest;
import com.qrpublic.apartment.saleenv.response.ListEnvironmentResponse;
import com.qrpublic.apartment.service.AdminService;
import com.qrpublic.apartment.service.NotificationService;
import com.qrpublic.apartment.service.RequestService;
import com.qrpublic.apartment.template.ResponseEntityConvertor;
import com.qrpublic.apartment.user.request.ListUserRequest;
import com.qrpublic.apartment.user.response.ListUserResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

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
}
