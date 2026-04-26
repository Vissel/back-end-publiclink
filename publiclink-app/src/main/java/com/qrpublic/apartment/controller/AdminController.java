package com.qrpublic.apartment.controller;

import com.qrpublic.apartment.requestmodel.Pagination;
import com.qrpublic.apartment.requestmodel.RequestDTO;
import com.qrpublic.apartment.requestmodel.SaleEnvDTO;
import com.qrpublic.apartment.requestmodel.SellerDTO;
import com.qrpublic.apartment.saleenv.SaleEnvironmentService;
import com.qrpublic.apartment.saleenv.request.ListEnvironmentRequest;
import com.qrpublic.apartment.service.RequestService;
import com.qrpublic.apartment.user.PubUserService;
import com.qrpublic.apartment.user.request.ListUserRequest;
import com.qrpublic.apartment.user.response.ListUserResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/admin/generator")
public class AdminController {
    @Autowired
    RequestService requestService;

    @Autowired
    SaleEnvironmentService envService;

    @Autowired
    PubUserService pubUserService;

    /**
     * Display environment list for administrator to manage, including the link generation
     * TODO
     *
     * @return
     */
    @GetMapping("/getAllEnvironment")
    public ResponseEntity<List<SaleEnvDTO>> getAllEnvironment(@RequestBody Pagination<ListEnvironmentRequest> request) {
        return ResponseEntity.ok(envService.getAllEnvironment());
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

        return pubUserService.listUser(request)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.NO_CONTENT).build());
    }
}
