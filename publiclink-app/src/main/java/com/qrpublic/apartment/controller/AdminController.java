package com.qrpublic.apartment.controller;

import com.qrpublic.apartment.model.SellerDTO;
import com.qrpublic.apartment.requestmodel.Pagination;
import com.qrpublic.apartment.requestmodel.RequestDTO;
import com.qrpublic.apartment.requestmodel.SaleEnvDTO;
import com.qrpublic.apartment.saleenv.SaleEnvironmentService;
import com.qrpublic.apartment.saleenv.request.ListEnvironmentRequest;
import com.qrpublic.apartment.service.AdminService;
import com.qrpublic.apartment.service.RequestService;
import com.qrpublic.apartment.user.request.ListUserRequest;
import com.qrpublic.apartment.user.response.ListUserResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/admin/v1")
public class AdminController {
    @Autowired
    RequestService requestService;

    @Autowired
    SaleEnvironmentService envService;

    @Autowired
    AdminService adminService;

    /**
     * Display environment list for administrator to manage, including the link generation
     * TODO
     *
     * @return
     */
    @PostMapping("/getAllEnvironment")
    public Mono<ResponseEntity<List<SaleEnvDTO>>> getAllEnvironment(@RequestBody Pagination<ListEnvironmentRequest> request) {
        return adminService.getSaleEnvironment(request)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.NO_CONTENT).build());
    }

    /**
     * Administrator create request, product and response access generation page Not used
     */
    @PostMapping("/generationPage")
//    @PreAuthorize(value = "hasRole('Admin')")
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
}
