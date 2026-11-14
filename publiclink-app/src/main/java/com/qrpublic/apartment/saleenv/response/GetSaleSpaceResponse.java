package com.qrpublic.apartment.saleenv.response;

import com.qrpublic.apartment.requestmodel.BaseResponse;
import com.qrpublic.apartment.requestmodel.OrderDTO;
import com.qrpublic.apartment.requestmodel.ProductDTO;
import lombok.Data;

import java.util.List;

@Data
public class GetSaleSpaceResponse extends BaseResponse {
    private String publicLink;
    private String envState;
    private String createdAt;
    private String endedAt;
    private String plannedEndedAt;
    /**
     * When the sale window opens; empty for environments created before it existed.
     */
    private String plannedStartedAt;
    /**
     * True while the window has not opened yet, computed against DB time so a
     * browser clock cannot decide it. Orders are refused in this state and the
     * public page hides the order form.
     */
    private Boolean notStartedYet;
    private List<ProductDTO> listProduct;
    private List<OrderDTO> listOrder;
    private String sellerFullName;

    private Boolean isSellerView;
    private int availableAmount;

    /**
     * STANDARD or YOGA_BOOKING (from the first product). The frontend renders
     * the yoga calendar instead of the order form when YOGA_BOOKING.
     */
    private String productType;
}
