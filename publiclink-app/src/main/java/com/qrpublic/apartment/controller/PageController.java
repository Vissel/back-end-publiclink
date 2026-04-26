package com.qrpublic.apartment.controller;

import com.qrpublic.apartment.core.service.CoreRequestService;
import com.qrpublic.apartment.entity.Request;
import com.qrpublic.apartment.entity.SaleEnvironment;
import com.qrpublic.apartment.requestmodel.OrderDTO;
import com.qrpublic.apartment.saleenv.SaleEnvironmentService;
import com.qrpublic.apartment.service.LinkService;
import com.qrpublic.apartment.service.OrderService;
import com.qrpublic.apartment.service.generating.JwtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/v1/publish")
public class PageController {

    @Autowired
    private LinkService linkService;
    @Autowired
    private SaleEnvironmentService envService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private CoreRequestService coreRequestService;
    @Autowired
    private JwtService jwtService;

    @GetMapping("/testGet")
    public Mono<ResponseEntity<String>> index() {
        return Mono.just(ResponseEntity.ok("Home response"));
    }

    /**
     * Determine the direction of seller. Redirect to:
     * - /api/v1/publish/saleUrl if the link is valid and not expired, username is existed in database and seller has valid JWT token.
     * + If the seller has invalid JWT token (expired), redirect to /login.
     * - /api/v1/publish/register, if the link is valid and not expired, username is not exist in database.
     *
     * @param token
     * @param headers
     * @return
     */
    @GetMapping("/link")
    public Mono<Void> handleSecureLink(@RequestParam String reqId, @RequestParam String token,
                                       @RequestHeader Map<String, String> headers,
                                       ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            if (!linkService.validateLink(token)) {
                return null;
            }
            Optional<Request> requestOpt = coreRequestService.getRequestByUuid(reqId);
            if (requestOpt.isEmpty()) {
                return null;
            }
            Request request = requestOpt.get();
            if (request.getSellerId() != null) {
                String authorization = headers.get("authorization");
                if (authorization != null && jwtService.isHeaderTokenValid(authorization)) {
                    return "/api/v1/publish/saleUrl";
                }
                return "/login";
            }
            return "/api/v1/publish/register";
        }).subscribeOn(Schedulers.boundedElastic()).flatMap(destination -> {
            ServerHttpResponse response = exchange.getResponse();
            if (destination == null) {
                response.setStatusCode(HttpStatus.BAD_REQUEST);
                return response.setComplete();
            }
            response.setStatusCode(HttpStatus.FOUND);
            response.getHeaders().setLocation(URI.create(destination));
            return response.setComplete();
        });
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
