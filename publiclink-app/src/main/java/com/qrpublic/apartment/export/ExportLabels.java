package com.qrpublic.apartment.export;

import lombok.Getter;

/**
 * Localized labels for seller Excel reports.
 * Mirrors the frontend i18n report vocabulary (EN / VI).
 */
@Getter
public final class ExportLabels {

    private final String sheetReport;
    private final String sheetSummary;
    private final String sheetEnvPrefix;

    private final String sectionRequestInfo;
    private final String sectionEnvironmentInfo;
    private final String sectionProducts;
    private final String sectionOrders;

    private final String requestUuid;
    private final String sellerName;
    private final String description;
    private final String createdAt;
    private final String authenticated;
    private final String authLink;
    private final String createdBy;

    private final String envId;
    private final String publicLink;
    private final String state;
    private final String endedAt;
    private final String willEndAt;

    private final String productName;
    private final String amount;
    private final String unit;
    private final String price;
    private final String totalAmount;

    private final String buyerName;
    private final String orderedAt;
    private final String note;
    private final String sellerNote;
    private final String delivered;
    private final String paid;

    private final String summaryIndex;
    private final String summaryProducts;
    private final String summaryOrders;

    private final String yes;
    private final String no;
    private final String notAvailable;

    private ExportLabels(
            String sheetReport,
            String sheetSummary,
            String sheetEnvPrefix,
            String sectionRequestInfo,
            String sectionEnvironmentInfo,
            String sectionProducts,
            String sectionOrders,
            String requestUuid,
            String sellerName,
            String description,
            String createdAt,
            String authenticated,
            String authLink,
            String createdBy,
            String envId,
            String publicLink,
            String state,
            String endedAt,
            String willEndAt,
            String productName,
            String amount,
            String unit,
            String price,
            String totalAmount,
            String buyerName,
            String orderedAt,
            String note,
            String sellerNote,
            String delivered,
            String paid,
            String summaryIndex,
            String summaryProducts,
            String summaryOrders,
            String yes,
            String no,
            String notAvailable) {
        this.sheetReport = sheetReport;
        this.sheetSummary = sheetSummary;
        this.sheetEnvPrefix = sheetEnvPrefix;
        this.sectionRequestInfo = sectionRequestInfo;
        this.sectionEnvironmentInfo = sectionEnvironmentInfo;
        this.sectionProducts = sectionProducts;
        this.sectionOrders = sectionOrders;
        this.requestUuid = requestUuid;
        this.sellerName = sellerName;
        this.description = description;
        this.createdAt = createdAt;
        this.authenticated = authenticated;
        this.authLink = authLink;
        this.createdBy = createdBy;
        this.envId = envId;
        this.publicLink = publicLink;
        this.state = state;
        this.endedAt = endedAt;
        this.willEndAt = willEndAt;
        this.productName = productName;
        this.amount = amount;
        this.unit = unit;
        this.price = price;
        this.totalAmount = totalAmount;
        this.buyerName = buyerName;
        this.orderedAt = orderedAt;
        this.note = note;
        this.sellerNote = sellerNote;
        this.delivered = delivered;
        this.paid = paid;
        this.summaryIndex = summaryIndex;
        this.summaryProducts = summaryProducts;
        this.summaryOrders = summaryOrders;
        this.yes = yes;
        this.no = no;
        this.notAvailable = notAvailable;
    }

    public static ExportLabels forLocale(String locale) {
        return ExportLocale.VIETNAMESE.equals(ExportLocale.resolve(locale)) ? vietnamese() : english();
    }

    public String envSheetName(int index) {
        return sheetEnvPrefix + index;
    }

    public String yesNo(boolean value) {
        return value ? yes : no;
    }

    public String orNotAvailable(String value) {
        return value != null && !value.isBlank() ? value : notAvailable;
    }

    private static ExportLabels english() {
        return new ExportLabels(
                "Report",
                "Summary",
                "Env-",
                "Request Information",
                "Sale Environment Information",
                "Products",
                "Orders",
                "Request UUID",
                "Seller Name",
                "Description",
                "Created At",
                "Authenticated",
                "Auth Link",
                "Created By",
                "Env ID",
                "Public Link",
                "State",
                "Ended At",
                "Will End At",
                "Product Name",
                "Amount",
                "Unit",
                "Price",
                "Total Amount",
                "Buyer Name",
                "Ordered At",
                "Note",
                "Seller Note",
                "Delivered",
                "Paid",
                "#",
                "Products",
                "Orders",
                "Yes",
                "No",
                "N/A");
    }

    private static ExportLabels vietnamese() {
        return new ExportLabels(
                "Báo cáo",
                "Tổng hợp",
                "MT-",
                "Thông tin yêu cầu",
                "Thông tin môi trường bán hàng",
                "Sản phẩm",
                "Đơn hàng",
                "Mã yêu cầu",
                "Tên người bán",
                "Mô tả",
                "Ngày tạo",
                "Đã xác thực",
                "Liên kết xác thực",
                "Tạo bởi",
                "Mã môi trường",
                "Liên kết công khai",
                "Trạng thái",
                "Ngày kết thúc",
                "Ngày kết thúc dự kiến",
                "Tên sản phẩm",
                "Số lượng",
                "Đơn vị",
                "Giá",
                "Tổng số lượng",
                "Tên người mua",
                "Thời gian đặt hàng",
                "Ghi chú",
                "Ghi chú người bán",
                "Đã giao",
                "Đã thanh toán",
                "#",
                "Sản phẩm",
                "Đơn hàng",
                "Có",
                "Không",
                "Không có");
    }
}
