package com.qrpublic.apartment.controller;

import com.qrpublic.apartment.requestmodel.Pagination;
import com.qrpublic.apartment.saleenv.SellerService;
import com.qrpublic.apartment.saleenv.request.ListSellerRequestsRequest;
import com.qrpublic.apartment.saleenv.response.ListSellerRequestResponse;
import com.qrpublic.apartment.template.ResponseEntityConvertor;
import com.qrpublic.apartment.user.PubUserService;
import com.qrpublic.apartment.user.request.GetSellerRequest;
import com.qrpublic.apartment.user.response.GetUserResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/seller")
public class SellerPageController {
    @Autowired
    PubUserService pubUserService;

    @Autowired
    SellerService sellerService;


    @PostMapping("/getInfo")
    public Mono<ResponseEntity<GetUserResponse>> getInfo(@RequestBody GetSellerRequest getSellerRequest) {
        return ResponseEntityConvertor.convertToMonoResponseEntity(pubUserService.getUserInfo(getSellerRequest));
    }

    @PostMapping("/listRequest")
    public Mono<ResponseEntity<ListSellerRequestResponse>> listRequest(@RequestBody Pagination<ListSellerRequestsRequest> request) {
        return ResponseEntityConvertor.convertToMonoResponseEntity(sellerService.listRequestEnvironment(request));
    }

}
