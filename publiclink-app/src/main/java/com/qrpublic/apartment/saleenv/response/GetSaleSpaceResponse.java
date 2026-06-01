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
    private List<ProductDTO> listProduct;
    private List<OrderDTO> listOrder;
    private String sellerFullName;

    private Boolean isSellerView;
}
