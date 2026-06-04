package com.qrpublic.apartment.saleenv;

import com.qrpublic.apartment.saleenv.response.GetSaleSpaceResponse;
import com.qrpublic.apartment.template.model.Result;

import java.util.Map;

/**
 * Space service interface for retrieving sale space details
 * Authorization: Seller, Buyer
 */
public interface SaleSpaceService {
    Result<GetSaleSpaceResponse> getSaleSpace(String token, Map<String, Object> headers);

    String getDBTimezone();
}
