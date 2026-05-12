package com.qrpublic.apartment.saleenv.impl;

import com.qrpublic.apartment.constant.CommonConstant;
import com.qrpublic.apartment.entity.SaleEnvironment;
import com.qrpublic.apartment.repository.SaleEnvironmentRepository;
import com.qrpublic.apartment.requestmodel.OrderDTO;
import com.qrpublic.apartment.requestmodel.Pagination;
import com.qrpublic.apartment.requestmodel.SaleEnvDTO;
import com.qrpublic.apartment.saleenv.SellerService;
import com.qrpublic.apartment.saleenv.convertor.SaleEnvConvertor;
import com.qrpublic.apartment.saleenv.request.ListSellerRequestsRequest;
import com.qrpublic.apartment.saleenv.response.ListSellerRequestResponse;
import com.qrpublic.apartment.template.model.Result;
import com.qrpublic.apartment.template.service.ProcessCallback;
import com.qrpublic.apartment.template.service.PublicLinkServiceTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

@Slf4j
@Service
@PreAuthorize("hasRole('Seller')")
public class SellerServiceImpl implements SellerService {

    @Autowired
    private SaleEnvironmentRepository saleEnvironmentRepository;

    @Autowired
    private PublicLinkServiceTemplate publicLinkServiceTemplate;

    @Override
    public Result<ListSellerRequestResponse> listRequestEnvironment(Pagination<ListSellerRequestsRequest> listRequestEnvironmentRequest) {
        return
                publicLinkServiceTemplate.execute(new ProcessCallback<Pagination<ListSellerRequestsRequest>, ListSellerRequestResponse>() {
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
                                                                  getRequest().getPage(),
                                                                  getRequest().getSize(),
                                                                  Sort.by(Sort.Order.desc("createdAt"))
                                                          );

                                                          Page<SaleEnvironment> resultPage = saleEnvironmentRepository
                                                                  .findBySellerName(sellerName, pageable);

                                                          List<SaleEnvDTO> envDTOs = resultPage.getContent().stream()
                                                                  .map(SaleEnvConvertor::buildEnvDTO)
                                                                  .map(SaleEnvDTO.SaleEnvDTOBuilder::build)
                                                                  .toList();

                                                          ListSellerRequestResponse response = new ListSellerRequestResponse();
                                                          response.setTotal((int) resultPage.getTotalElements());
                                                          response.setListSaleEnv(envDTOs);
                                                          return response;
                                                      }
                                                  }
                );
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
