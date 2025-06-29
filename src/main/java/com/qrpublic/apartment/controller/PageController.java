package com.qrpublic.apartment.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.qrpublic.apartment.entity.SaleEnvironment;
import com.qrpublic.apartment.requestmodel.OrderDTO;
import com.qrpublic.apartment.service.LinkService;
import com.qrpublic.apartment.service.OrderService;
import com.qrpublic.apartment.service.SaleEnvironmentService;
import com.qrpublic.apartment.util.Utils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/public")
public class PageController {

	@Autowired
	private LinkService linkService;
	@Autowired
	private SaleEnvironmentService envService;

	@Autowired
	private OrderService orderService;

	@GetMapping("/index")
	public ResponseEntity<String> index() {
		return ResponseEntity.ok("Home response");
	}

	@GetMapping("/link")
	public ResponseEntity<?> handleSecureLink(@RequestParam String token, @RequestHeader Map<String, String> headers) {
		try {
			if (linkService.validateLink(token)) {
				SaleEnvironment env = envService.getEnvironmentByPublicLink(token);
				return ResponseEntity.ok(Utils.createEnvDTO(env));
			}
			return ResponseEntity.badRequest().body("Link invalid");

		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Link cannot be access.");
		}
	}

	@PostMapping("/order")
	public ResponseEntity<?> addOrder(@RequestBody OrderDTO requestNewOrder) {
		if (linkService.validateLink(requestNewOrder.getToken())) {
			SaleEnvironment env = envService.getEnvironmentByPublicLink(requestNewOrder.getToken());
			if (env != null) {
				OrderDTO orderDTO = orderService.addNewOrder(env, requestNewOrder);
				if (orderDTO != null) {
					return ResponseEntity.ok(orderDTO);
				}
			}
		}
		return ResponseEntity.badRequest().body("Cannot add new order.");
	}

	@PostMapping("/order/delivery")
	public ResponseEntity<?> setDelivery(@RequestBody OrderDTO requestNewOrder, @RequestParam boolean delivered) {
		if (linkService.validateLink(requestNewOrder.getToken())) {
			boolean isDelivered = orderService.setDelivery(requestNewOrder.getOrderId(), delivered);
			log.info("Isdelivered:{}", isDelivered);
			return ResponseEntity.ok(isDelivered);
		}
		return ResponseEntity.badRequest().body("Update delivery failure");
	}

	@PostMapping("/order/getmoney")
	public ResponseEntity<?> setGetMoney(@RequestBody OrderDTO requestNewOrder, @RequestParam boolean getMoney) {
		if (linkService.validateLink(requestNewOrder.getToken())) {
			boolean isGetMoney = orderService.setGetMoney(requestNewOrder.getOrderId(), getMoney);
			log.info("Isgetmoney:{}", isGetMoney);
			return ResponseEntity.ok(isGetMoney);
		}
		return ResponseEntity.badRequest().body("Update getMoney failure");
	}

	@PostMapping("/order/note")
	public ResponseEntity<?> setGetMoney(@RequestBody OrderDTO requestNewOrder) {
		if (linkService.validateLink(requestNewOrder.getToken())) {
			boolean isUpdate = orderService.setSellerNote(requestNewOrder);
			return ResponseEntity.ok(isUpdate);
		}
		return ResponseEntity.badRequest().body("Update note failure");
	}
}
