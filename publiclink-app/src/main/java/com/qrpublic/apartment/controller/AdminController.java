package com.qrpublic.apartment.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.qrpublic.apartment.authentication.service.JwtService;
import com.qrpublic.apartment.entity.SaleEnvironment;
import com.qrpublic.apartment.requestmodel.RequestDTO;
import com.qrpublic.apartment.requestmodel.SaleEnvDTO;
import com.qrpublic.apartment.requestmodel.SellerDTO;
import com.qrpublic.apartment.service.RequestService;
import com.qrpublic.apartment.service.SaleEnvironmentService;
import com.qrpublic.apartment.util.Utils;

@RestController
@RequestMapping("/admin/generator")
public class AdminController {
	@Autowired
	RequestService requestService;

	@Autowired
	SaleEnvironmentService envService;

	@Autowired
    JwtService jwtUtils;

	/**
	 * Display an area to admin select and input seller info
	 * 
	 * @return
	 */
	@GetMapping("/home")
	public ResponseEntity<List<SaleEnvDTO>> home(@RequestHeader Map<String, String> headers) {
		String token = headers.get("authorization");
		if (jwtUtils.isTokenValid(token.substring(7))) {
			List<SaleEnvironment> saleEnv = envService.getAllEnvironment();
			List<SaleEnvDTO> envDTOs = saleEnv.stream().map(env -> Utils.createEnvDTO(env))
					.collect(Collectors.toList());

			return ResponseEntity.ok(envDTOs);
		}
		return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
	}

	/**
	 * 
	 * Administrator create request, product and response access generation page Not
	 * used
	 */
	@PostMapping("/generationPage")
	@PreAuthorize(value = "hasRole('Admin')")
	public ResponseEntity<RequestDTO> getGenerationPage(@RequestBody SellerDTO sellerDTO) {
		// create request
		RequestDTO requestDTO = requestService.createTempRequestDTO(sellerDTO);

		// response, UI navigate to the link generation page
		return ResponseEntity.ok(requestDTO);
	}

}
