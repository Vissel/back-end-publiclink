package com.qrpublic.apartment.export;

import com.qrpublic.apartment.constant.CommonConstant;
import com.qrpublic.apartment.entity.Product;
import com.qrpublic.apartment.entity.Request;
import com.qrpublic.apartment.entity.SaleEnvironment;
import com.qrpublic.apartment.repository.ProductRepository;
import com.qrpublic.apartment.repository.RequestRepository;
import com.qrpublic.apartment.repository.SaleEnvironmentRepository;
import com.qrpublic.apartment.saleenv.request.ExportAllRequest;
import com.qrpublic.apartment.saleenv.request.ExportReportRequest;
import com.qrpublic.apartment.util.DateUtils;
import com.qrpublic.apartment.util.Utils;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

@Slf4j
@Service
public class ExportService {

    @Autowired
    private RequestRepository requestRepository;

    @Autowired
    private SaleEnvironmentRepository saleEnvironmentRepository;

    @Autowired
    private ProductRepository productRepository;

    @Transactional(readOnly = true)
    public byte[] generateXlsxSingleReport(ExportReportRequest request) {
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
            List<Product> products,
            List<com.qrpublic.apartment.entity.Order> orders) {
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

            // ---- Single Sheet: All Data ----
            Sheet sheet = workbook.createSheet("Report");
            int rowIdx = 0;

            // Section 1: Request Information
            Row sectionRow = sheet.createRow(rowIdx++);
            Cell sectionCell = sectionRow.createCell(0);
            sectionCell.setCellValue("Request Information");
            sectionCell.setCellStyle(headerStyle);

            String[][] reqData = {
                    { "Request UUID", req.getReqUUID() },
                    { "Seller Name", req.getSellerName() != null ? req.getSellerName() : "" },
                    { "Description", req.getDescription() != null ? req.getDescription() : "" },
                    { "Created At", Utils.formatTimeStamp(req.getCreatedAt()) },
                    { "Authenticated", String.valueOf(req.isAuthenticated()) },
                    { "Auth Link", req.getReqAuthLink() != null ? req.getReqAuthLink() : "" },
                    { "Created By", req.getCreatedBy() != null ? req.getCreatedBy().getName() : "" }
            };
            for (int i = 0; i < reqData.length; i++) {
                Row row = sheet.createRow(rowIdx++);
                Cell keyCell = row.createCell(0);
                keyCell.setCellValue(reqData[i][0]);
                keyCell.setCellStyle(cellStyle);
                Cell valCell = row.createCell(1);
                valCell.setCellValue(reqData[i][1]);
                valCell.setCellStyle(cellStyle);
            }

            // Section 2: Sale Environment Information
            rowIdx++;
            sectionRow = sheet.createRow(rowIdx++);
            sectionCell = sectionRow.createCell(0);
            sectionCell.setCellValue("Sale Environment Information");
            sectionCell.setCellStyle(headerStyle);

            String[][] envData = {
                    { "Env ID", env != null ? env.getEnvId() : "N/A" },
                    { "Public Link", env != null ? env.getPublicLink() : "N/A" },
                    { "State", env != null ? String.valueOf(env.isState()) : "N/A" },
                    { "Created At", env != null ? Utils.formatTimeStamp(env.getCreatedAt()) : "N/A" },
                    { "Ended At", env != null ? DateUtils.dateToLocalTimeString(env.getEndedAt()) : "N/A" },
                    { "Will End At", env != null ? DateUtils.dateToLocalTimeString(env.getWillEndedAt()) : "N/A" }
            };
            for (int i = 0; i < envData.length; i++) {
                Row row = sheet.createRow(rowIdx++);
                Cell keyCell = row.createCell(0);
                keyCell.setCellValue(envData[i][0]);
                keyCell.setCellStyle(cellStyle);
                Cell valCell = row.createCell(1);
                valCell.setCellValue(envData[i][1]);
                valCell.setCellStyle(cellStyle);
            }

            // Section 3: Products
            rowIdx++;
            sectionRow = sheet.createRow(rowIdx++);
            sectionCell = sectionRow.createCell(0);
            sectionCell.setCellValue("Products");
            sectionCell.setCellStyle(headerStyle);

            String[] prodHeaders = { "Product Name", "Amount", "Unit", "Price", "Total Amount" };
            Row prodHeaderRow = sheet.createRow(rowIdx++);
            for (int i = 0; i < prodHeaders.length; i++) {
                Cell cell = prodHeaderRow.createCell(i);
                cell.setCellValue(prodHeaders[i]);
                cell.setCellStyle(headerStyle);
            }
            if (products != null && !products.isEmpty()) {
                for (Product p : products) {
                    Row row = sheet.createRow(rowIdx++);
                    createCell(row, 0, p.getProductName() != null ? p.getProductName() : "", cellStyle);
                    createCell(row, 1, p.getAmount(), cellStyle);
                    createCell(row, 2, p.getUnit() != null ? p.getUnit() : "", cellStyle);
                    createCell(row, 3, p.getPrice() != null ? p.getPrice().doubleValue() : 0.0, cellStyle);
                    createCell(row, 4, p.getTotal_amount(), cellStyle);
                }
            }

            // Section 4: Orders
            rowIdx++;
            sectionRow = sheet.createRow(rowIdx++);
            sectionCell = sectionRow.createCell(0);
            sectionCell.setCellValue("Orders");
            sectionCell.setCellStyle(headerStyle);

            String[] orderHeaders = { "Buyer Name", "Ordered At", "Amount", "Unit", "Note", "Seller Note", "Delivered",
                    "Get Money" };
            Row orderHeaderRow = sheet.createRow(rowIdx++);
            for (int i = 0; i < orderHeaders.length; i++) {
                Cell cell = orderHeaderRow.createCell(i);
                cell.setCellValue(orderHeaders[i]);
                cell.setCellStyle(headerStyle);
            }
            if (orders != null && !orders.isEmpty()) {
                SimpleDateFormat formatter = new SimpleDateFormat(CommonConstant.DATETIME_PATTERN);
                formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
                for (com.qrpublic.apartment.entity.Order o : orders) {
                    Row row = sheet.createRow(rowIdx++);
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

            // Auto-size columns (max columns used: 8 for orders)
            ((SXSSFSheet) sheet).trackAllColumnsForAutoSizing();
            for (int i = 0; i < 8; i++) {
                sheet.autoSizeColumn(i);
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

    // ─── Streaming Export for All Seller Reports ────────────────────────────────

    /**
     * Build the filename for the all-reports export.
     */
    public String buildAllReportsFileName(ExportAllRequest request) {
        String sellerName = request.getSellerName() != null ? request.getSellerName() : "unknown";
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        return "seller-report-" + sellerName + "-" + timestamp + ".xlsx";
    }

    /**
     * Generate all seller reports as a byte array.
     * Wraps {@link #streamingExportAllReports} with a ByteArrayOutputStream.
     */
    @Transactional(readOnly = true)
    public byte[] generateAllReportsBytes(ExportAllRequest request) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            streamingExportAllReports(request, baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate all reports Excel", e);
        }
    }

    /**
     * Stream all seller reports to the output stream.
     * Uses SXSSFWorkbook for memory-efficient streaming of large Excel files.
     * 
     * Unlike {@link #generateXlsxSingleReport} which returns byte[], this method
     * writes directly to the OutputStream, avoiding buffering the entire file in
     * memory.
     */
    @Transactional(readOnly = true)
    public void streamingExportAllReports(ExportAllRequest request, OutputStream outputStream) throws IOException {
        String sellerName = request.getSellerName();
        if (sellerName == null || sellerName.isBlank()) {
            throw new IllegalArgumentException("sellerName is required");
        }

        // Fetch all sale environments for this seller
        List<SaleEnvironment> environments = saleEnvironmentRepository.findAllBySellerName(sellerName);
        if (environments.isEmpty()) {
            throw new EntityNotFoundException("No environments found for seller: " + sellerName);
        }

        SXSSFWorkbook workbook = new SXSSFWorkbook(100); // keep 100 rows in memory at a time
        try {
            // Create shared styles once
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle cellStyle = createCellStyle(workbook);

            // Create a sheet for each environment
            for (int envIdx = 0; envIdx < environments.size(); envIdx++) {
                SaleEnvironment env = environments.get(envIdx);
                Request req = env.getRequest();
                if (req == null)
                    continue;

                // Sheet name must be ≤31 chars and unique
                String sheetName = "Env-" + (envIdx + 1);
                Sheet sheet = workbook.createSheet(sheetName);

                // Load products and orders for this environment
                List<Product> products = productRepository.findProductsByRequest(req);
                List<com.qrpublic.apartment.entity.Order> orders = saleEnvironmentRepository
                        .findWithOrdersById(env.getEnvId())
                        .map(SaleEnvironment::getListOrder)
                        .orElse(List.of());

                writeEnvironmentSheet(sheet, req, env, products, orders, headerStyle, cellStyle);
            }

            // Summary sheet at the end
            writeSummarySheet(workbook, environments, headerStyle, cellStyle);

            workbook.write(outputStream);
            workbook.dispose();
        } finally {
            workbook.close();
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle createCellStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private void writeEnvironmentSheet(Sheet sheet, Request req, SaleEnvironment env,
            List<Product> products,
            List<com.qrpublic.apartment.entity.Order> orders,
            CellStyle headerStyle, CellStyle cellStyle) {
        // Section 1: Request Info
        int rowIdx = 0;
        rowIdx = writeSectionHeader(sheet, rowIdx, "Request Information", headerStyle);
        rowIdx = writeKeyValueRow(sheet, rowIdx, "Request UUID", req.getReqUUID(), cellStyle);
        rowIdx = writeKeyValueRow(sheet, rowIdx, "Seller Name", req.getSellerName(), cellStyle);
        rowIdx = writeKeyValueRow(sheet, rowIdx, "Description", req.getDescription(), cellStyle);
        rowIdx = writeKeyValueRow(sheet, rowIdx, "Created At", Utils.formatTimeStamp(req.getCreatedAt()), cellStyle);
        rowIdx = writeKeyValueRow(sheet, rowIdx, "Authenticated", String.valueOf(req.isAuthenticated()), cellStyle);
        rowIdx = writeKeyValueRow(sheet, rowIdx, "Auth Link", req.getReqAuthLink() != null ? req.getReqAuthLink() : "",
                cellStyle);
        rowIdx = writeKeyValueRow(sheet, rowIdx, "Created By",
                req.getCreatedBy() != null ? req.getCreatedBy().getName() : "", cellStyle);

        // Section 2: Environment Info
        rowIdx++;
        rowIdx = writeSectionHeader(sheet, rowIdx, "Environment Information", headerStyle);
        rowIdx = writeKeyValueRow(sheet, rowIdx, "Env ID", env.getEnvId(), cellStyle);
        rowIdx = writeKeyValueRow(sheet, rowIdx, "Public Link", env.getPublicLink(), cellStyle);
        rowIdx = writeKeyValueRow(sheet, rowIdx, "State", String.valueOf(env.isState()), cellStyle);
        rowIdx = writeKeyValueRow(sheet, rowIdx, "Created At", Utils.formatTimeStamp(env.getCreatedAt()), cellStyle);
        rowIdx = writeKeyValueRow(sheet, rowIdx, "Ended At", DateUtils.dateToLocalTimeString(env.getEndedAt()),
                cellStyle);
        rowIdx = writeKeyValueRow(sheet, rowIdx, "Will End At", DateUtils.dateToLocalTimeString(env.getWillEndedAt()),
                cellStyle);

        // Section 3: Products
        rowIdx++;
        rowIdx = writeSectionHeader(sheet, rowIdx, "Products", headerStyle);
        String[] prodHeaders = { "Product Name", "Amount", "Unit", "Price", "Total Amount" };
        Row prodHeaderRow = sheet.createRow(rowIdx++);
        for (int i = 0; i < prodHeaders.length; i++) {
            Cell cell = prodHeaderRow.createCell(i);
            cell.setCellValue(prodHeaders[i]);
            cell.setCellStyle(headerStyle);
        }
        if (products != null) {
            for (Product p : products) {
                Row row = sheet.createRow(rowIdx++);
                createCell(row, 0, p.getProductName() != null ? p.getProductName() : "", cellStyle);
                createCell(row, 1, p.getAmount(), cellStyle);
                createCell(row, 2, p.getUnit() != null ? p.getUnit() : "", cellStyle);
                createCell(row, 3, p.getPrice() != null ? p.getPrice().doubleValue() : 0.0, cellStyle);
                createCell(row, 4, p.getTotal_amount(), cellStyle);
            }
        }

        // Section 4: Orders
        rowIdx++;
        rowIdx = writeSectionHeader(sheet, rowIdx, "Orders", headerStyle);
        String[] orderHeaders = { "Buyer Name", "Ordered At", "Amount", "Unit", "Note", "Seller Note", "Delivered",
                "Paid" };
        Row orderHeaderRow = sheet.createRow(rowIdx++);
        for (int i = 0; i < orderHeaders.length; i++) {
            Cell cell = orderHeaderRow.createCell(i);
            cell.setCellValue(orderHeaders[i]);
            cell.setCellStyle(headerStyle);
        }
        if (orders != null && !orders.isEmpty()) {
            SimpleDateFormat formatter = new SimpleDateFormat(CommonConstant.DATETIME_PATTERN);
            formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
            for (com.qrpublic.apartment.entity.Order o : orders) {
                Row row = sheet.createRow(rowIdx++);
                createCell(row, 0, o.getBuyerName() != null ? o.getBuyerName() : "", cellStyle);
                String orderTime = o.getOrderedAt() != null ? formatter.format(new Date(o.getOrderedAt().getTime()))
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

        // Auto-size columns (max columns used: 8 for orders)
        ((SXSSFSheet) sheet).trackAllColumnsForAutoSizing();
        for (int i = 0; i < 8; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void writeSummarySheet(SXSSFWorkbook workbook, List<SaleEnvironment> environments,
            CellStyle headerStyle, CellStyle cellStyle) {
        Sheet summarySheet = workbook.createSheet("Summary");
        String[] headers = { "#", "Request UUID", "Seller Name", "Public Link", "Products", "Orders", "Created At" };
        Row headerRow = summarySheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        for (int i = 0; i < environments.size(); i++) {
            SaleEnvironment env = environments.get(i);
            Request req = env.getRequest();
            Row row = summarySheet.createRow(i + 1);
            createCell(row, 0, i + 1, cellStyle);
            createCell(row, 1, req != null ? req.getReqUUID() : "", cellStyle);
            createCell(row, 2, req != null ? req.getSellerName() : "", cellStyle);
            createCell(row, 3, env.getPublicLink() != null ? env.getPublicLink() : "", cellStyle);
            int productCount = req != null ? productRepository.findProductsByRequest(req).size() : 0;
            createCell(row, 4, productCount, cellStyle);
            int orderCount = saleEnvironmentRepository.findWithOrdersById(env.getEnvId())
                    .map(e -> e.getListOrder().size()).orElse(0);
            createCell(row, 5, orderCount, cellStyle);
            createCell(row, 6, env.getCreatedAt() != null ? Utils.formatTimeStamp(env.getCreatedAt()) : "", cellStyle);
        }

        ((SXSSFSheet) summarySheet).trackAllColumnsForAutoSizing();
        for (int i = 0; i < headers.length; i++) {
            summarySheet.autoSizeColumn(i);
        }
    }

    private int writeSectionHeader(Sheet sheet, int rowIdx, String title, CellStyle headerStyle) {
        Row row = sheet.createRow(rowIdx);
        Cell cell = row.createCell(0);
        cell.setCellValue(title);
        cell.setCellStyle(headerStyle);
        return rowIdx + 1;
    }

    private int writeKeyValueRow(Sheet sheet, int rowIdx, String key, String value, CellStyle cellStyle) {
        Row row = sheet.createRow(rowIdx);
        createCell(row, 0, key, cellStyle);
        createCell(row, 1, value != null ? value : "", cellStyle);
        return rowIdx + 1;
    }
}
