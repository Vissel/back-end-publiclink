package com.qrpublic.apartment.saleenv;

import com.qrpublic.apartment.saleenv.request.GetSaleSpaceRequest;
import com.qrpublic.apartment.saleenv.response.GetSaleSpaceResponse;
import com.qrpublic.apartment.template.model.Result;

/**
 * Space service interface for retrieving sale space details
 * Authorization: Seller, Buyer
 */
public interface SaleSpaceService {
    Result<GetSaleSpaceResponse> getSaleSpace(GetSaleSpaceRequest getSaleSpaceRequest);
}
