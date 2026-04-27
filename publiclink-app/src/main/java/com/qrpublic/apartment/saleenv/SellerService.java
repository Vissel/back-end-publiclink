package com.qrpublic.apartment.saleenv;

import com.qrpublic.apartment.requestmodel.Pagination;
import com.qrpublic.apartment.saleenv.request.ListSellerRequestsRequest;
import com.qrpublic.apartment.saleenv.response.ListSellerRequestResponse;
import com.qrpublic.apartment.template.model.Result;

public interface SellerService {
    Result<ListSellerRequestResponse> listRequestEnvironment(Pagination<ListSellerRequestsRequest> listRequestEnvironmentRequest);
}
