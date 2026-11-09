package com.qrpublic.apartment.controller;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.qrpublic.apartment.requestmodel.VietQrRequest;
import com.qrpublic.apartment.service.VietQrService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class VietQrController {

	private final VietQrService vietQrService;

	@PostMapping("/qr")
	public ResponseEntity<ByteArrayResource> generateQr(@RequestBody VietQrRequest request) {
		byte[] imageBytes = vietQrService.generateQrImage(request);

		ByteArrayResource resource = new ByteArrayResource(imageBytes);
		return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).contentLength(imageBytes.length).body(resource);
	}

	@GetMapping("/link")
	public ResponseEntity<ByteArrayResource> generateQrViaLink(@RequestParam String bankCode,
			@RequestParam String accountNumber, @RequestParam String accountName,
			@RequestParam(required = false) Double amount, @RequestParam(required = false) String description) {
		VietQrRequest request = new VietQrRequest();
		request.setBankCode(bankCode);
		request.setAccountNumber(accountNumber);
		request.setAccountName(accountName);
		request.setAmount(amount);
		request.setDescription(description);

		byte[] imageBytes = vietQrService.generateQrImage(request);

		ByteArrayResource resource = new ByteArrayResource(imageBytes);
		return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).contentLength(imageBytes.length).body(resource);
	}
}