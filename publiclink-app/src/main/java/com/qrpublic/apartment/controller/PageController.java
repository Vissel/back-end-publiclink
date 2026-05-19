package com.qrpublic.apartment.controller;

import com.qrpublic.apartment.core.model.EnvStateEnum;
import com.qrpublic.apartment.entity.SaleEnvironment;
import com.qrpublic.apartment.exception.ResourceNotFoundException;
import com.qrpublic.apartment.requestmodel.OrderDTO;
import com.qrpublic.apartment.requestmodel.SaleEnvDTO;
import com.qrpublic.apartment.saleenv.SaleEnvironmentService;
import com.qrpublic.apartment.saleenv.SaleSpaceService;
import com.qrpublic.apartment.saleenv.request.GetSaleSpaceRequest;
import com.qrpublic.apartment.saleenv.response.GetSaleSpaceResponse;
import com.qrpublic.apartment.saleenv.response.SaleUrlResponse;
import com.qrpublic.apartment.service.LinkService;
import com.qrpublic.apartment.service.OrderService;
import com.qrpublic.apartment.service.RedirectionService;
import com.qrpublic.apartment.template.ResponseEntityConvertor;
import com.qrpublic.apartment.user.PubUserService;
import com.qrpublic.apartment.user.request.SellerRegisterRequest;
import com.qrpublic.apartment.user.response.SellerRegisterResponse;
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
    private RedirectionService redirectionService;
    @Autowired
    SaleSpaceService saleSpaceService;

    @Autowired
    PubUserService pubUserService;

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
    public Mono<Void> handleSecureLink(@RequestParam String reqUuid, @RequestParam String token,
                                       @RequestHeader Map<String, String> headers,
                                       ServerWebExchange exchange) {
        return Mono.fromCallable(() -> redirectionService.resolveDestination(reqUuid, token, headers))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(destination -> {
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

    @GetMapping("/getSaleUrl")
    public Mono<ResponseEntity<SaleUrlResponse>> getSaleUrl(@RequestParam String requestUuid) {
        return Mono.fromCallable(() -> {
                    SaleEnvDTO dto = envService.getSaleEnvironmentByRequestUuid(requestUuid);
                    return SaleUrlResponse.builder()
                            .sellerName(dto.getSellerName())
                            .publicLink(dto.getPublicLink())
                            .envState(dto.isEnvStatus() ? EnvStateEnum.ACTIVE.name() : EnvStateEnum.INACTIVE.name())
                            .build();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .map(ResponseEntity::ok)
                .onErrorResume(ResourceNotFoundException.class, e ->
                        Mono.just(ResponseEntity.notFound().build()));
    }

    @PostMapping("/publink")
    public Mono<ResponseEntity<GetSaleSpaceResponse>> accessPublicLink(@RequestBody GetSaleSpaceRequest getSaleSpaceRequest) {
        return ResponseEntityConvertor.convertToMonoResponseEntity(saleSpaceService.getSaleSpace(getSaleSpaceRequest));
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

    /**
     * when seller redirect to register page and do register.
     *
     * @param request
     * @return
     */
    @PostMapping("/register")
    Mono<ResponseEntity<SellerRegisterResponse>> register(@RequestBody SellerRegisterRequest request) {
        return pubUserService.registerNewSeller(request);
    }
}
