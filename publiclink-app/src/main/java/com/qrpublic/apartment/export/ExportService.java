package com.qrpublic.apartment.export;

import com.qrpublic.apartment.constant.CommonConstant;
import com.qrpublic.apartment.core.model.YogaBookingModel;
import com.qrpublic.apartment.core.model.YogaClassModel;
import com.qrpublic.apartment.core.model.YogaOccurrenceModel;
import com.qrpublic.apartment.core.service.CoreYogaBookingService;
import com.qrpublic.apartment.core.service.CoreYogaClassService;
import com.qrpublic.apartment.core.service.CoreYogaEnvironmentService;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
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

    @Autowired
    private CoreYogaClassService coreYogaClassService;

    @Autowired
    private CoreYogaBookingService coreYogaBookingService;

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

        // Yoga environments keep their data in the yoga_* tables rather than in
        // plain product orders: the report shows the class schedule with live
        // slot accounting and the registered buyers instead.
        boolean isYoga = env != null && products != null && !products.isEmpty()
                && CoreYogaEnvironmentService.TYPE_YOGA.equals(products.get(0).getProductType());
        List<YogaOccurrenceModel> yogaOccurrences = null;
        List<YogaBookingModel> yogaBookings = null;
        if (isYoga) {
            LocalDate from = env.getCreatedAt() != null
                    ? env.getCreatedAt().toLocalDateTime().toLocalDate()
                    : LocalDate.now();
            LocalDate to = env.getWillEndedAt() != null
                    ? env.getWillEndedAt().toLocalDateTime().toLocalDate()
                    : from.plusDays(92);
            if (to.isBefore(from)) {
                to = from;
            }
            yogaOccurrences = assembleYogaOccurrences(env.getEnvId(), from, to);
            yogaBookings = coreYogaBookingService.findByEnvAndRange(env.getEnvId(), from, to);
        }

        return generateExcel(req, env, products, orders, yogaOccurrences, yogaBookings,
                ExportLabels.forLocale(request.getLocale()), ExportYogaLabels.forLocale(request.getLocale()));
    }

    /**
     * One row per concrete weekly occurrence of every active class inside the
     * environment window, with the live booked/remaining slot accounting.
     */
    private List<YogaOccurrenceModel> assembleYogaOccurrences(String envId, LocalDate from, LocalDate to) {
        List<YogaClassModel> classes = coreYogaClassService.findActiveByEnv(envId);
        if (classes.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> bookedCounts = coreYogaBookingService.countBookedByEnvRange(envId, from, to);
        List<YogaOccurrenceModel> occurrences = new ArrayList<>();
        for (YogaClassModel yogaClass : classes) {
            for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
                if (date.getDayOfWeek().getValue() != yogaClass.getDayOfWeek()) {
                    continue;
                }
                int booked = bookedCounts.getOrDefault(
                        CoreYogaBookingService.slotKey(yogaClass.getClassCode(), date), 0);
                occurrences.add(YogaOccurrenceModel.builder()
                        .classCode(yogaClass.getClassCode())
                        .date(date.toString())
                        .dayOfWeek(yogaClass.getDayOfWeek())
                        .startTime(yogaClass.getStartTime())
                        .endTime(yogaClass.getEndTime())
                        .title(yogaClass.getTitle())
                        .coach(yogaClass.getCoach())
                        .capacity(yogaClass.getCapacity())
                        .booked(booked)
                        .remaining(Math.max(0, yogaClass.getCapacity() - booked))
                        .build());
            }
        }
        return occurrences;
    }

    private byte[] generateExcel(Request req, SaleEnvironment env,
            List<Product> products,
            List<com.qrpublic.apartment.entity.Order> orders,
            List<YogaOccurrenceModel> yogaOccurrences,
            List<YogaBookingModel> yogaBookings,
            ExportLabels labels,
            ExportYogaLabels yogaLabels) {
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
            Sheet sheet = workbook.createSheet(labels.getSheetReport());
            int rowIdx = 0;

            // Section 1: Request Information
            Row sectionRow = sheet.createRow(rowIdx++);
            Cell sectionCell = sectionRow.createCell(0);
            sectionCell.setCellValue(labels.getSectionRequestInfo());
            sectionCell.setCellStyle(headerStyle);

            String[][] reqData = {
                    { labels.getRequestUuid(), req.getReqUUID() },
                    { labels.getSellerName(), req.getSellerName() != null ? req.getSellerName() : "" },
                    { labels.getDescription(), req.getDescription() != null ? req.getDescription() : "" },
                    { labels.getCreatedAt(), Utils.formatTimeStamp(req.getCreatedAt()) },
                    { labels.getCreatedBy(), req.getCreatedBy() != null ? req.getCreatedBy().getName() : "" }
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
            sectionCell.setCellValue(labels.getSectionEnvironmentInfo());
            sectionCell.setCellStyle(headerStyle);

            String[][] envData = {
                    { labels.getEnvId(), env != null ? env.getEnvId() : labels.getNotAvailable() },
                    { labels.getPublicLink(), env != null ? env.getPublicLink() : labels.getNotAvailable() },
                    { labels.getState(),
                            env != null ? labels.activeInactive(env.isState()) : labels.getNotAvailable() },
                    { labels.getCreatedAt(),
                            env != null ? Utils.formatTimeStamp(env.getCreatedAt()) : labels.getNotAvailable() },
                    { labels.getEndedAt(),
                            env != null ? DateUtils.dateToLocalTimeString(env.getEndedAt())
                                    : labels.getNotAvailable() },
                    { labels.getWillEndAt(),
                            env != null ? DateUtils.dateToLocalTimeString(env.getWillEndedAt())
                                    : labels.getNotAvailable() }
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

            // Yoga environments export their class schedule and registered
            // buyers; standard environments keep the product and order lists.
            boolean yogaReport = yogaOccurrences != null;
            if (yogaReport) {
                rowIdx = writeYogaClassesSection(sheet, rowIdx, yogaOccurrences, yogaLabels, headerStyle,
                        cellStyle);
                rowIdx = writeYogaBookingsSection(sheet, rowIdx, yogaBookings, labels, yogaLabels, headerStyle,
                        cellStyle);
            } else {
                rowIdx = writeProductsSection(sheet, rowIdx, products, labels, headerStyle, cellStyle);
                rowIdx = writeOrdersSection(sheet, rowIdx, orders, labels, headerStyle, cellStyle);
            }

            // Auto-size columns (orders use 8, the yoga booking list 11)
            int maxColumns = yogaReport ? 11 : 8;
            ((SXSSFSheet) sheet).trackAllColumnsForAutoSizing();
            for (int i = 0; i < maxColumns; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(baos);
            workbook.dispose();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate Excel report", e);
        }
    }

    private int writeProductsSection(Sheet sheet, int rowIdx, List<Product> products, ExportLabels labels,
            CellStyle headerStyle, CellStyle cellStyle) {
        rowIdx++;
        Row sectionRow = sheet.createRow(rowIdx++);
        Cell sectionCell = sectionRow.createCell(0);
        sectionCell.setCellValue(labels.getSectionProducts());
        sectionCell.setCellStyle(headerStyle);

        String[] prodHeaders = {
                labels.getProductName(),
                labels.getAmount(),
                labels.getUnit(),
                labels.getPrice(),
                labels.getTotalAmount()
        };
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
        return rowIdx;
    }

    private int writeOrdersSection(Sheet sheet, int rowIdx, List<com.qrpublic.apartment.entity.Order> orders,
            ExportLabels labels, CellStyle headerStyle, CellStyle cellStyle) {
        rowIdx++;
        Row sectionRow = sheet.createRow(rowIdx++);
        Cell sectionCell = sectionRow.createCell(0);
        sectionCell.setCellValue(labels.getSectionOrders());
        sectionCell.setCellStyle(headerStyle);

        String[] orderHeaders = {
                labels.getBuyerName(),
                labels.getOrderedAt(),
                labels.getAmount(),
                labels.getUnit(),
                labels.getNote(),
                labels.getSellerNote(),
                labels.getDelivered(),
                labels.getPaid()
        };
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
                createCell(row, 6, labels.yesNo(o.isDelivered()), cellStyle);
                createCell(row, 7, labels.yesNo(o.isGetMoney()), cellStyle);
            }
        }
        return rowIdx;
    }

    /**
     * Yoga class schedule: one row per concrete weekly occurrence inside the
     * environment window, with the live booked/remaining slot accounting.
     */
    private int writeYogaClassesSection(Sheet sheet, int rowIdx, List<YogaOccurrenceModel> occurrences,
            ExportYogaLabels labels, CellStyle headerStyle, CellStyle cellStyle) {
        rowIdx++;
        Row sectionRow = sheet.createRow(rowIdx++);
        Cell sectionCell = sectionRow.createCell(0);
        sectionCell.setCellValue(labels.getSectionClasses());
        sectionCell.setCellStyle(headerStyle);

        String[] headers = {
                labels.getClassLabel(),
                labels.getCoach(),
                labels.getWeekday(),
                labels.getTime(),
                labels.getCapacity(),
                labels.getBooked(),
                labels.getRemaining()
        };
        Row headerRow = sheet.createRow(rowIdx++);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        for (YogaOccurrenceModel occ : occurrences) {
            Row row = sheet.createRow(rowIdx++);
            createCell(row, 0, occ.getTitle() != null ? occ.getTitle() : "", cellStyle);
            createCell(row, 1, occ.getCoach() != null ? occ.getCoach() : "", cellStyle);
            createCell(row, 2, labels.weekdayName(occ.getDayOfWeek()), cellStyle);
            createCell(row, 3, (occ.getStartTime() != null ? occ.getStartTime() : "")
                    + "-" + (occ.getEndTime() != null ? occ.getEndTime() : ""), cellStyle);
            createCell(row, 4, occ.getCapacity(), cellStyle);
            createCell(row, 5, occ.getBooked(), cellStyle);
            createCell(row, 6, occ.getRemaining(), cellStyle);
        }
        return rowIdx;
    }

    /**
     * Registered buyers of the yoga environment: one row per booking, mirroring
     * the seller's attendee list columns.
     */
    private int writeYogaBookingsSection(Sheet sheet, int rowIdx, List<YogaBookingModel> bookings,
            ExportLabels labels, ExportYogaLabels yogaLabels, CellStyle headerStyle, CellStyle cellStyle) {
        rowIdx++;
        Row sectionRow = sheet.createRow(rowIdx++);
        Cell sectionCell = sectionRow.createCell(0);
        sectionCell.setCellValue(yogaLabels.getSectionBookings());
        sectionCell.setCellStyle(headerStyle);

        String[] headers = {
                labels.getBuyerName(),
                yogaLabels.getUsername(),
                yogaLabels.getClassLabel(),
                yogaLabels.getDate(),
                yogaLabels.getTime(),
                yogaLabels.getPaymentMode(),
                yogaLabels.getPaid(),
                yogaLabels.getAttended(),
                yogaLabels.getBookedAt(),
                labels.getNote(),
                yogaLabels.getStatus()
        };
        Row headerRow = sheet.createRow(rowIdx++);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        for (YogaBookingModel b : bookings) {
            Row row = sheet.createRow(rowIdx++);
            String buyer = b.getBuyerName() != null && !b.getBuyerName().isBlank()
                    ? b.getBuyerName()
                    : (b.getBuyerUsername() != null ? b.getBuyerUsername() : "");
            createCell(row, 0, buyer, cellStyle);
            createCell(row, 1, b.getBuyerUsername() != null ? b.getBuyerUsername() : "", cellStyle);
            createCell(row, 2, b.getTitle() != null ? b.getTitle() : "", cellStyle);
            createCell(row, 3, b.getDate() != null ? b.getDate() : "", cellStyle);
            createCell(row, 4, (b.getStartTime() != null ? b.getStartTime() : "")
                    + "-" + (b.getEndTime() != null ? b.getEndTime() : ""), cellStyle);
            createCell(row, 5, yogaLabels.paymentModeLabel(b.getPaymentMode()), cellStyle);
            createCell(row, 6, labels.yesNo(b.isPaid()), cellStyle);
            createCell(row, 7, labels.yesNo(b.isAttended()), cellStyle);
            createCell(row, 8, b.getBookedAt() != null ? b.getBookedAt() : "", cellStyle);
            createCell(row, 9, b.getNote() != null ? b.getNote() : "", cellStyle);
            createCell(row, 10, yogaLabels.statusLabel(b.getStatus()), cellStyle);
        }
        return rowIdx;
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
        String prefix = ExportLocale.VIETNAMESE.equals(ExportLocale.resolve(request.getLocale()))
                ? "bao-cao"
                : "seller-report";
        return prefix + "-" + sellerName + "-" + timestamp + ".xlsx";
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

        ExportLabels labels = ExportLabels.forLocale(request.getLocale());

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
                String sheetName = labels.envSheetName(envIdx + 1);
                Sheet sheet = workbook.createSheet(sheetName);

                // Load products and orders for this environment
                List<Product> products = productRepository.findProductsByRequest(req);
                List<com.qrpublic.apartment.entity.Order> orders = saleEnvironmentRepository
                        .findWithOrdersById(env.getEnvId())
                        .map(SaleEnvironment::getListOrder)
                        .orElse(List.of());

                writeEnvironmentSheet(sheet, req, env, products, orders, headerStyle, cellStyle, labels);
            }

            // Summary sheet at the end
            writeSummarySheet(workbook, environments, headerStyle, cellStyle, labels);

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
            CellStyle headerStyle, CellStyle cellStyle, ExportLabels labels) {
        // Section 1: Request Info
        int rowIdx = 0;
        rowIdx = writeSectionHeader(sheet, rowIdx, labels.getSectionRequestInfo(), headerStyle);
        rowIdx = writeKeyValueRow(sheet, rowIdx, labels.getRequestUuid(), req.getReqUUID(), cellStyle);
        rowIdx = writeKeyValueRow(sheet, rowIdx, labels.getSellerName(), req.getSellerName(), cellStyle);
        rowIdx = writeKeyValueRow(sheet, rowIdx, labels.getDescription(), req.getDescription(), cellStyle);
        rowIdx = writeKeyValueRow(sheet, rowIdx, labels.getCreatedAt(), Utils.formatTimeStamp(req.getCreatedAt()),
                cellStyle);
        rowIdx = writeKeyValueRow(sheet, rowIdx, labels.getCreatedBy(),
                req.getCreatedBy() != null ? req.getCreatedBy().getName() : "", cellStyle);

        // Section 2: Environment Info
        rowIdx++;
        rowIdx = writeSectionHeader(sheet, rowIdx, labels.getSectionEnvironmentInfo(), headerStyle);
        rowIdx = writeKeyValueRow(sheet, rowIdx, labels.getEnvId(), env.getEnvId(), cellStyle);
        rowIdx = writeKeyValueRow(sheet, rowIdx, labels.getPublicLink(), env.getPublicLink(), cellStyle);
        rowIdx = writeKeyValueRow(sheet, rowIdx, labels.getState(), labels.activeInactive(env.isState()), cellStyle);
        rowIdx = writeKeyValueRow(sheet, rowIdx, labels.getCreatedAt(), Utils.formatTimeStamp(env.getCreatedAt()),
                cellStyle);
        rowIdx = writeKeyValueRow(sheet, rowIdx, labels.getEndedAt(), DateUtils.dateToLocalTimeString(env.getEndedAt()),
                cellStyle);
        rowIdx = writeKeyValueRow(sheet, rowIdx, labels.getWillEndAt(),
                DateUtils.dateToLocalTimeString(env.getWillEndedAt()), cellStyle);

        // Section 3: Products
        rowIdx++;
        rowIdx = writeSectionHeader(sheet, rowIdx, labels.getSectionProducts(), headerStyle);
        String[] prodHeaders = {
                labels.getProductName(),
                labels.getAmount(),
                labels.getUnit(),
                labels.getPrice(),
                labels.getTotalAmount()
        };
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
        rowIdx = writeSectionHeader(sheet, rowIdx, labels.getSectionOrders(), headerStyle);
        String[] orderHeaders = {
                labels.getBuyerName(),
                labels.getOrderedAt(),
                labels.getAmount(),
                labels.getUnit(),
                labels.getNote(),
                labels.getSellerNote(),
                labels.getDelivered(),
                labels.getPaid()
        };
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
                createCell(row, 6, labels.yesNo(o.isDelivered()), cellStyle);
                createCell(row, 7, labels.yesNo(o.isGetMoney()), cellStyle);
            }
        }

        // Auto-size columns (max columns used: 8 for orders)
        ((SXSSFSheet) sheet).trackAllColumnsForAutoSizing();
        for (int i = 0; i < 8; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void writeSummarySheet(SXSSFWorkbook workbook, List<SaleEnvironment> environments,
            CellStyle headerStyle, CellStyle cellStyle, ExportLabels labels) {
        Sheet summarySheet = workbook.createSheet(labels.getSheetSummary());
        String[] headers = {
                labels.getSummaryIndex(),
                labels.getRequestUuid(),
                labels.getSellerName(),
                labels.getPublicLink(),
                labels.getSummaryProducts(),
                labels.getSummaryOrders(),
                labels.getCreatedAt()
        };
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
