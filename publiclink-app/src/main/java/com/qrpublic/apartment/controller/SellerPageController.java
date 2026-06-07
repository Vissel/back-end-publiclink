package com.qrpublic.apartment.controller;

import com.qrpublic.apartment.requestmodel.Pagination;
import com.qrpublic.apartment.requestmodel.UpdateProductRequest;
import com.qrpublic.apartment.saleenv.SaleSpaceService;
import com.qrpublic.apartment.saleenv.SellerService;
import com.qrpublic.apartment.saleenv.request.ExportReportRequest;
import com.qrpublic.apartment.saleenv.request.ListSellerRequestsRequest;
import com.qrpublic.apartment.saleenv.response.ListSellerRequestResponse;
import com.qrpublic.apartment.template.ResponseEntityConvertor;
import com.qrpublic.apartment.user.request.GetSellerRequest;
import com.qrpublic.apartment.user.response.GetUserResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/v1/seller")
public class SellerPageController {

    @Autowired
    SellerService sellerService;
    @Autowired
    SaleSpaceService saleSpaceService;

    @PostMapping("/getInfo")
    public Mono<ResponseEntity<GetUserResponse>> getInfo(@RequestBody GetSellerRequest getSellerRequest) {
        return ResponseEntityConvertor.convertToMonoResponseEntity(sellerService.getUserInfo(getSellerRequest));
    }

    @PostMapping("/listRequest")
    public Mono<ResponseEntity<ListSellerRequestResponse>> listRequest(@RequestBody Pagination<ListSellerRequestsRequest> request) {
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

}
