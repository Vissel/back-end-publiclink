package com.qrpublic.apartment.controller;

import com.qrpublic.apartment.export.ExportCache;
import com.qrpublic.apartment.export.ExportService;
import com.qrpublic.apartment.model.notification.NotificationCountResponse;
import com.qrpublic.apartment.model.notification.NotificationDTO;
import com.qrpublic.apartment.order.OrderService;
import com.qrpublic.apartment.order.request.GetMoneyRequest;
import com.qrpublic.apartment.order.request.SellerNoteRequest;
import com.qrpublic.apartment.order.request.SetDeliverRequest;
import com.qrpublic.apartment.requestmodel.Pagination;
import com.qrpublic.apartment.requestmodel.UpdateProductRequest;
import com.qrpublic.apartment.saleenv.SaleSpaceService;
import com.qrpublic.apartment.saleenv.SellerService;
import com.qrpublic.apartment.saleenv.request.ExportAllRequest;
import com.qrpublic.apartment.saleenv.request.ExportReportRequest;
import com.qrpublic.apartment.saleenv.request.ListSellerRequestsRequest;
import com.qrpublic.apartment.saleenv.response.ListSellerRequestResponse;
import com.qrpublic.apartment.service.LinkService;
import com.qrpublic.apartment.service.NotificationService;
import com.qrpublic.apartment.template.ResponseEntityConvertor;
import com.qrpublic.apartment.user.request.GetSellerRequest;
import com.qrpublic.apartment.user.response.GetUserResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/seller")
public class SellerPageController {

    @Autowired
    SellerService sellerService;
    @Autowired
    SaleSpaceService saleSpaceService;
    @Autowired
    ExportService exportService;
    @Autowired
    ExportCache exportCache;
    @Autowired
    LinkService linkService;
    @Autowired
    OrderService orderService;
    @Autowired
    NotificationService notificationService;

    @PostMapping("/getInfo")
    public Mono<ResponseEntity<GetUserResponse>> getInfo(@RequestBody GetSellerRequest getSellerRequest) {
        return ResponseEntityConvertor.convertToMonoResponseEntity(sellerService.getUserInfo(getSellerRequest));
    }

    @PostMapping("/listRequest")
    public Mono<ResponseEntity<ListSellerRequestResponse>> listRequest(
            @RequestBody Pagination<ListSellerRequestsRequest> request) {
        return ResponseEntityConvertor.convertToMonoResponseEntity(sellerService.listRequestEnvironment(request));
    }

    @PostMapping("/product/update")
    public ResponseEntity<?> updateProduct(@RequestBody UpdateProductRequest request) {
        try {
            boolean success = saleSpaceService.updateProduct(request);
            return ResponseEntity.ok(success);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/export")
    public Mono<ResponseEntity<byte[]>> exportReport(@RequestBody ExportReportRequest request) {
        return Mono.fromCallable(() -> {
            byte[] excelData = sellerService.exportReport(request);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=export-" + request.getRequestUUID() + ".xlsx")
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(excelData);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ─── Streaming Export All Reports (Two-Step Pattern) ────────────────────────

    /**
     * Step 1: Receive export parameters, store in cache, return token.
     * <p>
     * This separates the (potentially large) request payload from the download URL,
     * enabling true streaming via GET in step 2.
     */
    @PostMapping("/getExportToken")
    public ResponseEntity<Map<String, String>> prepareExportAll(@RequestBody ExportAllRequest request) {
        String token = exportCache.store(request);
        return ResponseEntity.ok(Map.of("downloadToken", token));
    }

    /**
     * Step 2: Generate and return the Excel file using the token from step 1.
     * <p>
     * Token is single-use (removed from cache after retrieval).
     * The Excel generation runs on a blocking-safe elastic scheduler.
     */
    @GetMapping("/stream/exportAll/{token}")
    public Mono<ResponseEntity<byte[]>> streamExportAll(@PathVariable String token) {
        ExportAllRequest request = exportCache.consume(token);
        if (request == null) {
            return Mono.just(ResponseEntity.notFound().build());
        }

        String fileName = exportService.buildAllReportsFileName(request);

        return Mono.fromCallable(() -> {
            byte[] excelData = exportService.generateAllReportsBytes(request);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(excelData);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/order/delivery")
    public Mono<Boolean> setDelivery(@RequestBody @Valid SetDeliverRequest request) {
        return Mono.fromCallable(() -> {
            return orderService.setDelivery(request);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/order/getmoney")
    public Mono<Boolean> setGetMoney(@RequestBody @Valid GetMoneyRequest request) {
        return Mono.fromCallable(() -> {
            return orderService.setGetMoney(request);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/order/note")
    public Mono<Boolean> setSellerNote(@RequestBody @Valid SellerNoteRequest request) {
        return Mono.fromCallable(() -> {
            return orderService.setSellerNote(request);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ─── Notification Endpoints ────────────────────────────────────────────────

    /**
     * Get unread notification count for seller (bell badge).
     */
    @GetMapping("/notifications/unread-count")
    public Mono<ResponseEntity<NotificationCountResponse>> getUnreadCount(
            @RequestHeader(value = "X-User-ID", defaultValue = "") String sellerUsername) {
        return ResponseEntityConvertor.convertToMonoResponseEntity(
                notificationService.getUnreadCount(sellerUsername));
    }

    /**
     * Get paginated notifications for seller.
     */
    @GetMapping("/notifications")
    public Mono<ResponseEntity<List<NotificationDTO>>> getNotifications(
            @RequestHeader(value = "X-User-ID", defaultValue = "") String sellerUsername,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntityConvertor.convertToMonoResponseEntity(
                notificationService.getNotifications(sellerUsername, page, size));
    }

    /**
     * Get notification detail (auto marks as read).
     */
    @GetMapping("/notifications/{id}")
    public Mono<ResponseEntity<NotificationDTO>> getNotificationDetail(
            @RequestHeader(value = "X-User-ID", defaultValue = "") String sellerUsername,
            @PathVariable Long id) {
        return ResponseEntityConvertor.convertToMonoResponseEntity(
                notificationService.getNotificationDetail(sellerUsername, id));
    }

    /**
     * Mark a single notification as read.
     */
    @PostMapping("/notifications/{id}/read")
    public Mono<ResponseEntity<Void>> markAsRead(
            @RequestHeader(value = "X-User-ID", defaultValue = "") String sellerUsername,
            @PathVariable Long id) {
        return ResponseEntityConvertor.convertToMonoResponseEntity(
                notificationService.markAsRead(sellerUsername, id));
    }

    /**
     * Mark all notifications as read for the seller.
     */
    @PostMapping("/notifications/read-all")
    public Mono<ResponseEntity<Void>> markAllAsRead(
            @RequestHeader(value = "X-User-ID", defaultValue = "") String sellerUsername) {
        return ResponseEntityConvertor.convertToMonoResponseEntity(
                notificationService.markAllAsRead(sellerUsername));
    }
}
