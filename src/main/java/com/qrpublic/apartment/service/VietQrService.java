package com.qrpublic.apartment.service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.qrpublic.apartment.config.JwtUtil;
import com.qrpublic.apartment.requestmodel.VietQrRequest;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class VietQrService extends JwtUtil implements LinkService {

	@Value("${jwt.secret.url}")
	private String secretKey;

	@Value("${jwt.url.expired}")
	private long ACCESS_TOKEN_VALIDITY; // 2 days as default

	@Value("${server.servlet.context-path}")
	private String contextPath;

	@Override
	protected byte[] getKey() {
		return this.secretKey.getBytes();
	}

	/**
	 * Generating url by jwt
	 * 
	 * @param idString
	 * @param reqId
	 * @return
	 */
	@Override
	public String generateSecureUrl(String idString, long reqId) {
		String jwt = Jwts.builder().setSubject(idString).claim("reqId", reqId).setIssuedAt(new Date())
				.signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(secretKey.getBytes())),
						SignatureAlgorithm.HS256)
				.setExpiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_VALIDITY)).compact();
		/*
		 * String hostBuild = CommonConstant.EMPTY; try { InetAddress inet =
		 * InetAddress.getLocalHost(); hostBuild += "http://" +
		 * inet.getCanonicalHostName() + ":8080" + contextPath;
		 * log.debug("Current host:{}", inet.getHostName()); hostBuild +=
		 * "/public/link?token=" + jwt; } catch (UnknownHostException e) {
		 * log.error("Unknowhost:{}", e.getMessage()); }
		 */
		return jwt;
	}

	public byte[] generateQrImage(VietQrRequest request) {
		String payload = generateVietQrPayload(request);
		try {
			BitMatrix bitMatrix = new MultiFormatWriter().encode(payload, BarcodeFormat.QR_CODE, 300, 300);
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
			return outputStream.toByteArray();
		} catch (Exception e) {
			throw new RuntimeException("Failed to generate QR", e);
		}
	}

	private String tlv(String tag, String value) {
		return tag + String.format("%02d", value.length()) + value;
	}

	private String generateVietQrPayload(VietQrRequest req) {
		String payload = "";
		payload += tlv("00", "01"); // Format
		payload += tlv("01", "11"); // Dynamic QR
		String merchantAccountInfo = tlv("00", "A000000727") + tlv("01", req.getBankCode())
				+ tlv("02", req.getAccountNumber()) + tlv("03", req.getAccountName());
		payload += tlv("38", merchantAccountInfo);
		payload += tlv("52", "0000");
		payload += tlv("53", "704"); // VND
		if (req.getAmount() != null) {
			payload += tlv("54", String.format("%.0f", req.getAmount()));
		}
		payload += tlv("58", "VN");
		payload += tlv("59", req.getAccountName().substring(0, Math.min(20, req.getAccountName().length())));
		payload += tlv("60", "HANOI");

		if (req.getDescription() != null) {
			String addData = tlv("08", req.getDescription());
			payload += tlv("62", addData);
		}

		String crcInput = payload + "6304";
		String crc = crc16CCITT(crcInput);
		payload += "6304" + crc;

		return payload;
	}

	private String crc16CCITT(String input) {
		int crc = 0xFFFF;
		byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
		for (byte b : bytes) {
			crc ^= (b & 0xFF) << 8;
			for (int i = 0; i < 8; i++) {
				if ((crc & 0x8000) != 0) {
					crc = (crc << 1) ^ 0x1021;
				} else {
					crc <<= 1;
				}
				crc &= 0xFFFF;
			}
		}
		return String.format("%04X", crc);
	}

	@Override
	public boolean validateLink(String publicToken) {
		boolean valid = isTokenValid(publicToken);
		if (valid) {
			log.info("link of:" + extractSubject(publicToken));
		}
		return valid;
	}
}
