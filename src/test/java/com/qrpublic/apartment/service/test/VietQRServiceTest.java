package com.qrpublic.apartment.service.test;

import com.qrpublic.apartment.service.VietQrService;

public class VietQRServiceTest {

	public static void main(String[] args) {
		VietQrService service = new VietQrService();
		String url = service.generateSecureUrl("aaa", 111);
		System.out.println(url);

	}

}
