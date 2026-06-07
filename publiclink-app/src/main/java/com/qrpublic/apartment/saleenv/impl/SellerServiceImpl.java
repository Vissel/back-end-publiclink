package com.qrpublic.apartment.saleenv.impl;

import com.qrpublic.apartment.constant.CommonConstant;
import com.qrpublic.apartment.entity.Product;
import com.qrpublic.apartment.entity.Request;
import com.qrpublic.apartment.entity.SaleEnvironment;
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
import com.qrpublic.apartment.saleenv.response.ListSellerRequestResponse;
import com.qrpublic.apartment.template.model.Result;
import com.qrpublic.apartment.template.service.ProcessCallback;
import com.qrpublic.apartment.template.service.PublicLinkServiceTemplate;
import com.qrpublic.apartment.user.PubUserService;
import com.qrpublic.apartment.user.request.GetSellerRequest;
import com.qrpublic.apartment.user.response.GetUserResponse;
import com.qrpublic.apartment.util.DateUtils;
import com.qrpublic.apartment.util.Utils;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
                                                                  Math.max(0, getRequest().getPage() - 1),
                                                                  getRequest().getSize(),
                                                                  Sort.by(Sort.Order.desc("createdAt"))
                                                          );
                                                          long total = saleEnvironmentRepository.countBySellerName(sellerName);
                                                          if (total == 0) return new ListSellerRequestResponse();

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
                                                                              ? withProducts.getRequest().getProducts() : List.of();
                                                                      List<com.qrpublic.apartment.entity.Order> orders = withOrders != null
                                                                              ? withOrders.getListOrder() : List.of();
                                                                      int orderTotal = orderCountMap.getOrDefault(env.getEnvId(), 0);
                                                                      int totalProductQuantity = productQtyMap.getOrDefault(env.getEnvId(), 0);
                                                                      return SaleEnvConvertor.buildEnvDTO(env, products, orders, orderTotal, totalProductQuantity);
                                                                  })
                                                                  .map(SaleEnvDTO.SaleEnvDTOBuilder::build)
                                                                  .toList();

                                                          ListSellerRequestResponse response = new ListSellerRequestResponse();
                                                          response.setTotal((int) total);
                                                          response.setListSaleEnv(envDTOs);
                                                          return response;
                                                      }
                                                  }
                );
    }

    @Override
    public Result<GetUserResponse> getUserInfo(GetSellerRequest getSellerRequest) {
        return pubUserService.getUserInfo(getSellerRequest);
    }

    @Override
    public byte[] exportReport(ExportReportRequest request) {
        String reqUUID = request.getRequestUUID();
        if (reqUUID == null || reqUUID.isBlank()) {
            throw new IllegalArgumentException("requestUUID is required");
        }

        // Fetch the Request entity
        Request req = requestRepository.findByReqUUID(reqUUID)
                .orElseThrow(() -> new EntityNotFoundException("Request not found: " + reqUUID));

        // Find associated SaleEnvironment
        SaleEnvironment env = saleEnvironmentRepository.findByRequestReqUuid(reqUUID)
                .orElse(null);

        // Load products for this request
        List<Product> products = productRepository.findProductsByRequest(req);

        // If we have an environment, load orders
        List<com.qrpublic.apartment.entity.Order> orders = new ArrayList<>();
        if (env != null) {
            orders = saleEnvironmentRepository.findWithOrdersById(env.getEnvId())
                    .map(SaleEnvironment::getListOrder)
                    .orElse(List.of());
        }

        return generateExcel(req, env, products, orders);
    }

    private byte[] generateExcel(Request req, SaleEnvironment env,
                                 List<Product> products, List<com.qrpublic.apartment.entity.Order> orders) {
        SXSSFWorkbook workbook = new SXSSFWorkbook();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            // Create cell styles
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            CellStyle cellStyle = workbook.createCellStyle();
            cellStyle.setBorderTop(BorderStyle.THIN);
            cellStyle.setBorderBottom(BorderStyle.THIN);
            cellStyle.setBorderLeft(BorderStyle.THIN);
            cellStyle.setBorderRight(BorderStyle.THIN);

            // ---- Sheet 1: Request Information ----
            Sheet reqSheet = workbook.createSheet("Request Info");
            String[] reqHeaders = {"Field", "Value"};
            Row headerRow = reqSheet.createRow(0);
            for (int i = 0; i < reqHeaders.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(reqHeaders[i]);
                cell.setCellStyle(headerStyle);
            }

            String[][] reqData = {
                    {"Request UUID", req.getReqUUID()},
                    {"Seller Name", req.getSellerName() != null ? req.getSellerName() : ""},
                    {"Description", req.getDescription() != null ? req.getDescription() : ""},
                    {"Created At", Utils.formatTimeStamp(req.getCreatedAt())},
                    {"Authenticated", String.valueOf(req.isAuthenticated())},
                    {"Auth Link", req.getReqAuthLink() != null ? req.getReqAuthLink() : ""},
                    {"Created By", req.getCreatedBy() != null ? req.getCreatedBy().getName() : ""}
            };

            for (int i = 0; i < reqData.length; i++) {
                Row row = reqSheet.createRow(i + 1);
                for (int j = 0; j < reqData[i].length; j++) {
                    Cell cell = row.createCell(j);
                    cell.setCellValue(reqData[i][j]);
                    cell.setCellStyle(cellStyle);
                }
            }
            reqSheet.autoSizeColumn(0);
            reqSheet.autoSizeColumn(1);

            // ---- Sheet 2: Sale Environment ----
            Sheet envSheet = workbook.createSheet("Environment");
            String[] envHeaders = {"Field", "Value"};
            headerRow = envSheet.createRow(0);
            for (int i = 0; i < envHeaders.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(envHeaders[i]);
                cell.setCellStyle(headerStyle);
            }

            String[][] envData = {
                    {"Env ID", env != null ? env.getEnvId() : "N/A"},
                    {"Public Link", env != null ? env.getPublicLink() : "N/A"},
                    {"State", env != null ? String.valueOf(env.isState()) : "N/A"},
                    {"Created At", env != null ? Utils.formatTimeStamp(env.getCreatedAt()) : "N/A"},
                    {"Ended At", env != null ? DateUtils.dateToLocalTimeString(env.getEndedAt()) : "N/A"},
                    {"Will End At", env != null ? DateUtils.dateToLocalTimeString(env.getWillEndedAt()) : "N/A"}
            };

            for (int i = 0; i < envData.length; i++) {
                Row row = envSheet.createRow(i + 1);
                for (int j = 0; j < envData[i].length; j++) {
                    Cell cell = row.createCell(j);
                    cell.setCellValue(envData[i][j]);
                    cell.setCellStyle(cellStyle);
                }
            }
            envSheet.autoSizeColumn(0);
            envSheet.autoSizeColumn(1);

            // ---- Sheet 3: Products ----
            Sheet prodSheet = workbook.createSheet("Products");
            String[] prodHeaders = {"Product Name", "Amount", "Unit", "Price", "Total Amount"};
            headerRow = prodSheet.createRow(0);
            for (int i = 0; i < prodHeaders.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(prodHeaders[i]);
                cell.setCellStyle(headerStyle);
            }

            if (products != null && !products.isEmpty()) {
                for (int i = 0; i < products.size(); i++) {
                    Product p = products.get(i);
                    Row row = prodSheet.createRow(i + 1);
                    createCell(row, 0, p.getProductName() != null ? p.getProductName() : "", cellStyle);
                    createCell(row, 1, p.getAmount(), cellStyle);
                    createCell(row, 2, p.getUnit() != null ? p.getUnit() : "", cellStyle);
                    createCell(row, 3, p.getPrice() != null ? p.getPrice().doubleValue() : 0.0, cellStyle);
                    createCell(row, 4, p.getTotal_amount(), cellStyle);
                }
            }
            for (int i = 0; i < prodHeaders.length; i++) {
                prodSheet.autoSizeColumn(i);
            }

            // ---- Sheet 4: Orders ----
            Sheet orderSheet = workbook.createSheet("Orders");
            String[] orderHeaders = {"Buyer Name", "Ordered At", "Amount", "Unit", "Note", "Seller Note", "Delivered", "Get Money"};
            headerRow = orderSheet.createRow(0);
            for (int i = 0; i < orderHeaders.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(orderHeaders[i]);
                cell.setCellStyle(headerStyle);
            }

            if (orders != null && !orders.isEmpty()) {
                SimpleDateFormat formatter = new SimpleDateFormat(CommonConstant.DATETIME_PATTERN);
                formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
                for (int i = 0; i < orders.size(); i++) {
                    com.qrpublic.apartment.entity.Order o = orders.get(i);
                    Row row = orderSheet.createRow(i + 1);
                    createCell(row, 0, o.getBuyerName() != null ? o.getBuyerName() : "", cellStyle);
                    String orderTime = o.getOrderedAt() != null
                            ? formatter.format(new Date(o.getOrderedAt().getTime()))
                            : "";
                    createCell(row, 1, orderTime, cellStyle);
                    createCell(row, 2, o.getAmount(), cellStyle);
                    createCell(row, 3, o.getUnit() != null ? o.getUnit() : "", cellStyle);
                    createCell(row, 4, o.getNote() != null ? o.getNote() : "", cellStyle);
                    createCell(row, 5, o.getSellerNote() != null ? o.getSellerNote() : "", cellStyle);
                    createCell(row, 6, o.isDelivered() ? "Yes" : "No", cellStyle);
                    createCell(row, 7, o.isGetMoney() ? "Yes" : "No", cellStyle);
                }
            }
            for (int i = 0; i < orderHeaders.length; i++) {
                orderSheet.autoSizeColumn(i);
            }

            workbook.write(baos);
            workbook.dispose();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate Excel report", e);
        }
    }

    private void createCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void createCell(Row row, int column, int value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void createCell(Row row, int column, double value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
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
