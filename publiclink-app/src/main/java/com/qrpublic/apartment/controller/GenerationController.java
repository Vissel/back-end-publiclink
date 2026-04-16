package com.qrpublic.apartment.controller;

import com.qrpublic.apartment.entity.SaleEnvironment;
import com.qrpublic.apartment.requestmodel.SaleEnvDTO;
import com.qrpublic.apartment.requestmodel.SellerDTO;
import com.qrpublic.apartment.service.RequestService;
import com.qrpublic.apartment.service.SaleEnvironmentService;
import com.qrpublic.apartment.service.generating.JwtService;
import com.qrpublic.apartment.util.Utils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/generator")
public class GenerationController {

    private final AdminController adminController;

    @Autowired
    RequestService requestService;

    @Autowired
    SaleEnvironmentService envService;

    @Autowired
    JwtService jwtService;

    GenerationController(AdminController adminController) {
        this.adminController = adminController;
    }

    /**
     * System generate a public link
     *
     */
    @PostMapping("/generatePublicLink")
//    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<?> generatePublicLink(@RequestBody SellerDTO sellerDTO) {
        log.info("Gerating public link for seller {}...", sellerDTO.getUsername());
        // check jwt expired
        if (jwtService.isTokenValid(sellerDTO.getAccessToken())) {
            String publicToken = requestService.generatePublicLink(sellerDTO);
            if (publicToken != null && !publicToken.isBlank()) {
                SaleEnvironment env = envService.getEnvironmentByPublicLink(publicToken);
                String sellerAuthLink = requestService.generateSellerAuthLink(env.getRequest().getSellerId(),
                        env.getRequest().getReqId());
                SaleEnvDTO envdto = Utils.createEnvDTO(env);
//						new SaleEnvDTO(Utils.formatTimeStamp(env.getCreatedAt()),
//						env.getRequest().getSellerId().getUserName(), env.getRequest().getSellerId().getLink(),
//						LinkUtils.buildPublicLink(publicToken), env.getRequest().getCreatedBy().getName(),
//						env.isState());

                envdto.setSellerAuthLink(sellerAuthLink);
                return ResponseEntity.ok(envdto);
            }
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Token is expired. Re-login");
        }
        return ResponseEntity.badRequest().body("Generating failure");
    }

}
