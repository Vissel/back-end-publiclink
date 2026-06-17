package com.qrpublic.apartment.order;

import com.qrpublic.apartment.constant.LinkConstant;
import com.qrpublic.apartment.entity.Order;
import com.qrpublic.apartment.entity.Product;
import com.qrpublic.apartment.entity.SaleEnvironment;
import com.qrpublic.apartment.exception.ApplicationException;
import com.qrpublic.apartment.model.convertor.OrderConvertor;
import com.qrpublic.apartment.order.request.GetMoneyRequest;
import com.qrpublic.apartment.order.request.SellerNoteRequest;
import com.qrpublic.apartment.order.request.SetDeliverRequest;
import com.qrpublic.apartment.repository.OrderRepository;
import com.qrpublic.apartment.repository.ProductRepository;
import com.qrpublic.apartment.repository.SaleEnvironmentRepository;
import com.qrpublic.apartment.requestmodel.OrderDTO;
import com.qrpublic.apartment.saleenv.request.AddOrderRequest;
import com.qrpublic.apartment.saleenv.response.AddOrderResponse;
import com.qrpublic.apartment.service.LinkService;
import com.qrpublic.apartment.template.model.Result;
import com.qrpublic.apartment.template.service.ProcessCallback;
import com.qrpublic.apartment.template.service.PublicLinkServiceTemplate;
import com.qrpublic.apartment.util.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Optional;

@Service
public class OrderImpl implements OrderService {
    @Autowired
    OrderRepository orderRepo;

    @Autowired
    SaleEnvironmentRepository saleEnvironmentRepo;

    @Autowired
    ProductRepository productRepo;

    @Autowired
    LinkService linkService;

    @Autowired
    PublicLinkServiceTemplate publicLinkServiceTemplate;

    private final TransactionTemplate transactionTemplate;

    public OrderImpl(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    }

    @Override
    public OrderDTO addNewOrder(SaleEnvironment environment, OrderDTO requestOrderDTO) {
        OrderDTO result = null;
        if (Utils.isValidStr(requestOrderDTO.getBuyer())) {
            long orderedTime = System.currentTimeMillis();
            Order newOrder = new Order();
            newOrder.setBuyerName(requestOrderDTO.getBuyer());
            newOrder.setAmount(requestOrderDTO.getAmount());
            newOrder.setNote(requestOrderDTO.getNote());
            newOrder.setSaleEnvironment(environment);

            Order order = orderRepo.save(newOrder);
            if (order.getOrderId() != 0) {
                result = OrderConvertor.createOrderDTO(order, orderedTime, environment.getPublicLink());
            }
        }
        return result;
    }

    @Override
    @Transactional
    public boolean setDelivery(SetDeliverRequest setDeliverRequest) {
        Optional<Order> order = orderRepo.findById(setDeliverRequest.getOrderId());
        if (order.isPresent()) {
            order.get().setDelivered(setDeliverRequest.getDelivered());
            return orderRepo.save(order.get()).isDelivered();
        }
        return false;
    }

    @Override
    @Transactional
    public boolean setGetMoney(GetMoneyRequest getMoneyRequest) {
        Optional<Order> order = orderRepo.findById(getMoneyRequest.getOrderId());
        if (order.isPresent()) {
            order.get().setGetMoney(getMoneyRequest.getGetMoney());
            return orderRepo.save(order.get()).isGetMoney();
        }
        return false;
    }

    @Override
    @Transactional
    public boolean setSellerNote(SellerNoteRequest request) {
        Optional<Order> findOrder = orderRepo.findById(request.getOrderId());
        if (findOrder.isPresent()) {
            findOrder.get().setSellerNote(request.getSellerNote());
            return orderRepo.save(findOrder.get()).getSellerNote().equals(request.getSellerNote());
        }
        return false;
    }

    @Override
    public Result<AddOrderResponse> addOrder(AddOrderRequest request) {
        return publicLinkServiceTemplate.execute(new ProcessCallback<AddOrderRequest, AddOrderResponse>() {
            @Override
            public AddOrderRequest getRequest() {
                return request;
            }

            @Override
            public void preProcess(AddOrderRequest req) {
                Assert.notNull(req, "Request cannot be null");
                Assert.hasText(req.getToken(), "Token is required");
                // 3. Validate the link/token
                Assert.isTrue(linkService.validateLink(req.getToken()), "Invalid or expired link");

                Assert.hasText(req.getBuyerName(), "Buyer name is required");
                Assert.isTrue(req.getAmount() > 0, "Order amount must be positive");
            }

            @Override
            public AddOrderResponse process() {
                return transactionTemplate.execute(status -> {
                    AddOrderRequest req = getRequest();
                    String requestUUID = (String) linkService.extractClaimByKey(req.getToken(),
                            LinkConstant.CLAIM_SUBJECT);
                    // 1. Find environment by public link with pessimistic write lock
                    SaleEnvironment env = saleEnvironmentRepo.findByPublicLinkWithWriteLock(requestUUID)
                            .orElseThrow(() -> new ApplicationException("Environment not found", 404));

                    // 2. Check environment is active
                    if (!env.isState()) {
                        throw new ApplicationException("Environment is inactive", 400);
                    }

                    // 4. Check product stock with write lock — ensure at least one product has
                    // remaining qty
                    List<Product> products = productRepo.findProductsByRequestWithWriteLock(env.getRequest());
                    boolean hasStock = products.stream()
                            .anyMatch(p -> p.getAmount() < p.getTotal_amount());
                    if (!hasStock) {
                        throw new ApplicationException("No products available", 400);
                    }

                    // 5. Create the order
                    Order order = new Order();
                    order.setBuyerName(req.getBuyerName());
                    order.setAmount(req.getAmount());
                    order.setUnit(req.getUnit());
                    order.setNote(req.getNote());
                    order.setSaleEnvironment(env);
                    order.setDelivered(false);
                    order.setGetMoney(false);
                    order = orderRepo.save(order);

                    // 6. Decrement stock: increment Product.amount by ordered quantity on first
                    // available product
                    for (Product p : products) {
                        if (p.getAmount() < p.getTotal_amount()) {
                            p.setAmount(p.getAmount() + req.getAmount());
                            productRepo.save(p);
                            break;
                        }
                    }

                    // 7. Build response
                    AddOrderResponse response = new AddOrderResponse();
                    response.setAddedAmount(req.getAmount());
                    return response;
                });
            }
        });
    }
}
