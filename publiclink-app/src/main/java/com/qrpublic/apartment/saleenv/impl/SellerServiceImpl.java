package com.qrpublic.apartment.saleenv.impl;

import com.qrpublic.apartment.constant.CommonConstant;
import com.qrpublic.apartment.core.service.CoreTimezoneService;
import com.qrpublic.apartment.entity.SaleEnvironment;
import com.qrpublic.apartment.exception.ApplicationException;
import com.qrpublic.apartment.exception.ResourceNotFoundException;
import com.qrpublic.apartment.export.ExportService;
import com.qrpublic.apartment.repository.ProductRepository;
import com.qrpublic.apartment.repository.RequestRepository;
import com.qrpublic.apartment.repository.SaleEnvironmentRepository;
import com.qrpublic.apartment.requestmodel.OrderDTO;
import com.qrpublic.apartment.requestmodel.Pagination;
import com.qrpublic.apartment.requestmodel.SaleEnvDTO;
import com.qrpublic.apartment.saleenv.SellerService;
import com.qrpublic.apartment.saleenv.convertor.SaleEnvConvertor;
import com.qrpublic.apartment.saleenv.request.ExportReportRequest;
import com.qrpublic.apartment.saleenv.request.ListSellerRequestsRequest;
import com.qrpublic.apartment.saleenv.request.UpdateEnvironmentWindowRequest;
import com.qrpublic.apartment.saleenv.response.ListSellerRequestResponse;
import com.qrpublic.apartment.template.model.Result;
import com.qrpublic.apartment.template.service.ProcessCallback;
import com.qrpublic.apartment.template.service.PublicLinkServiceTemplate;
import com.qrpublic.apartment.user.PubUserService;
import com.qrpublic.apartment.user.request.GetSellerRequest;
import com.qrpublic.apartment.user.response.GetUserResponse;
import com.qrpublic.apartment.util.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@PreAuthorize("hasRole('SELLER')")
public class SellerServiceImpl implements SellerService {

    @Autowired
    private SaleEnvironmentRepository saleEnvironmentRepository;

    @Autowired
    private PublicLinkServiceTemplate publicLinkServiceTemplate;

    @Autowired
    private PubUserService pubUserService;

    @Autowired
    private RequestRepository requestRepository;

    @Autowired
    private ProductRepository productRepository;
    @Autowired
    ExportService exportService;

    @Autowired
    private CoreTimezoneService coreTimezoneService;

    @Autowired
    private com.qrpublic.apartment.core.service.CoreSaleEnvironmentService coreSaleEnvironmentService;

    @Override
    public Result<ListSellerRequestResponse> listRequestEnvironment(
            Pagination<ListSellerRequestsRequest> listRequestEnvironmentRequest) {
        return publicLinkServiceTemplate
                .execute(new ProcessCallback<Pagination<ListSellerRequestsRequest>, ListSellerRequestResponse>() {
                    @Override
                    public Pagination<ListSellerRequestsRequest> getRequest() {
                        return listRequestEnvironmentRequest;
                    }

                    @Override
                    public void preProcess(Pagination<ListSellerRequestsRequest> request) {
                        Assert.notNull(request, "Request cannot be null");
                        Assert.notEmpty(request.getListData(), "Request data is required");
                        Assert.hasText(request.getListData().get(0).getSellerName(), "Seller name is required");
                        Assert.isTrue(request.getSize() > 0, "Page size must be greater than 0");
                    }

                    @Override
                    public ListSellerRequestResponse process() {
                        ListSellerRequestsRequest filter = getRequest().getListData().get(0);
                        String sellerName = filter.getSellerName();

                        Pageable pageable = PageRequest.of(
                                Math.max(0, getRequest().getPage() - 1),
                                getRequest().getSize(),
                                Sort.by(Sort.Order.desc("createdAt")));
                        long total = saleEnvironmentRepository.countBySellerName(sellerName);
                        if (total == 0)
                            return new ListSellerRequestResponse();

                        Page<SaleEnvironment> resultPage = saleEnvironmentRepository
                                .findBySellerName(sellerName, pageable);

                        List<String> envIds = resultPage.getContent().stream()
                                .map(SaleEnvironment::getEnvId)
                                .toList();

                        // Use scoped JOIN FETCH methods to load only current page's products and orders
                        Map<String, SaleEnvironment> withProductsMap = saleEnvironmentRepository
                                .findAllWithProductsByIds(envIds).stream()
                                .collect(Collectors.toMap(SaleEnvironment::getEnvId, Function.identity()));

                        Map<String, SaleEnvironment> withOrdersMap = saleEnvironmentRepository
                                .findAllWithOrdersByIds(envIds).stream()
                                .collect(Collectors.toMap(SaleEnvironment::getEnvId, Function.identity()));

                        // Lightweight count queries — no entity loading
                        Map<String, Integer> orderCountMap = saleEnvironmentRepository
                                .countOrdersByEnvIds(envIds).stream()
                                .collect(Collectors.toMap(
                                        row -> (String) row[0],
                                        row -> ((Long) row[1]).intValue()));

                        Map<String, Integer> productQtyMap = saleEnvironmentRepository
                                .sumProductQuantityByEnvIds(envIds).stream()
                                .collect(Collectors.toMap(
                                        row -> (String) row[0],
                                        row -> ((Number) row[1]).intValue()));

                        List<SaleEnvDTO> envDTOs = resultPage.getContent().stream()
                                .map(env -> {
                                    SaleEnvironment withProducts = withProductsMap.get(env.getEnvId());
                                    SaleEnvironment withOrders = withOrdersMap.get(env.getEnvId());
                                    List<com.qrpublic.apartment.entity.Product> products = withProducts != null
                                            ? withProducts.getRequest().getProducts()
                                            : List.of();
                                    List<com.qrpublic.apartment.entity.Order> orders = withOrders != null
                                            ? withOrders.getListOrder()
                                            : List.of();
                                    int orderTotal = orderCountMap.getOrDefault(env.getEnvId(), 0);
                                    int totalProductQuantity = productQtyMap.getOrDefault(env.getEnvId(), 0);
                                    return SaleEnvConvertor.buildEnvDTO(env, products, orders, orderTotal,
                                            totalProductQuantity);
                                })
                                .map(SaleEnvDTO.SaleEnvDTOBuilder::build)
                                .toList();

                        ListSellerRequestResponse response = new ListSellerRequestResponse();
                        response.setTotal((int) total);
                        response.setListSaleEnv(envDTOs);
                        return response;
                    }
                });
    }

    @Override
    public Result<GetUserResponse> getUserInfo(GetSellerRequest getSellerRequest) {
        return pubUserService.getUserInfo(getSellerRequest);
    }

    @Override
    public byte[] exportReport(ExportReportRequest request) {
        return exportService.generateXlsxSingleReport(request);
    }

    /**
     * Seller edit of the sale window: move the closing time earlier, or close
     * the sale immediately. Deliberately outside PublicLinkServiceTemplate and
     * throwing ApplicationException rather than returning a failed Result — a
     * failed Result renders an empty body through ResponseEntityConvertor, which
     * would hide the reason from the seller's edit dialog.
     *
     * <p>
     * The end can only move earlier, never later: the public link token was
     * minted to expire at the original end, so a later value would promise
     * validity the token cannot deliver. Shortening can never breach the plan
     * end either, which creation already enforced.
     */
    @Override
    @org.springframework.transaction.annotation.Transactional
    public SaleEnvDTO updateEnvironmentWindow(UpdateEnvironmentWindowRequest request, String sellerUsername) {
        if (request == null || !StringUtils.hasText(request.getRequestUuid()) || request.getEndAt() == null) {
            throw new ApplicationException("requestUuid and endAt are required.", 400);
        }
        if (!StringUtils.hasText(sellerUsername)) {
            throw new ApplicationException("Authentication is required.", 401);
        }

        final String uuid = request.getRequestUuid().trim();
        final SaleEnvironment env = saleEnvironmentRepository.findByRequestReqUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Environment not found: " + uuid));

        final String owner = env.getRequest() != null ? env.getRequest().getSellerName() : null;
        if (!sellerUsername.trim().equals(owner)) {
            throw new ApplicationException("This environment belongs to another seller.", 403);
        }

        final java.sql.Timestamp now = new java.sql.Timestamp(coreTimezoneService.getNow().getTime());
        if (!env.isState() || (env.getWillEndedAt() != null && !env.getWillEndedAt().after(now))) {
            throw new ApplicationException("This sale is already closed.", 400);
        }

        final java.sql.Timestamp newEnd = new java.sql.Timestamp(request.getEndAt());
        if (env.getWillEndedAt() != null && newEnd.after(env.getWillEndedAt())) {
            throw new ApplicationException("The end time cannot be later than the current planned end ("
                    + DateUtils.dateToLocalTimeString(env.getWillEndedAt()) + ").", 400);
        }

        if (!newEnd.after(now)) {
            // Closing now: flip state through the entity and stamp ended_at with
            // the scheduler's own update, because ended_at is mapped
            // insertable/updatable = false and cannot be written this way.
            env.setState(false);
            env.setWillEndedAt(now);
            saleEnvironmentRepository.save(env);
            coreSaleEnvironmentService.expireEnvironment(env.getEnvId(), now);
        } else {
            env.setWillEndedAt(newEnd);
            saleEnvironmentRepository.save(env);
        }
        log.info("Seller [{}] moved the closing time of [{}] to {}", sellerUsername, uuid, env.getWillEndedAt());
        return SaleEnvConvertor.buildEnvDTO(env).build();
    }

    private static List<OrderDTO> buildOrderDTOs(List<com.qrpublic.apartment.entity.Order> listOrder) {
        List<OrderDTO> orderDTOs = new ArrayList<>();
        if (listOrder != null && !listOrder.isEmpty()) {
            String publicLink = listOrder.get(0).getSaleEnvironment().getPublicLink();
            listOrder.forEach(o -> orderDTOs.add(buildOrderDTO(o, o.getOrderedAt().getTime(), publicLink)));
        }
        return orderDTOs;
    }

    private static OrderDTO buildOrderDTO(com.qrpublic.apartment.entity.Order order, long time, String publicLink) {
        SimpleDateFormat formatter = new SimpleDateFormat(CommonConstant.DATETIME_PATTERN);
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        String orderTime = formatter.format(new Date(time));
        return new OrderDTO(order.getOrderId(), orderTime, order.getBuyerName(), publicLink,
                order.isDelivered(), order.isGetMoney(), order.getSellerNote(),
                order.getAmount(), order.getUnit(), order.getNote());
    }

    private static <T> T getEntityOrDefault(T object) {
        return org.hibernate.Hibernate.isInitialized(object) ? object : null;
    }
}
