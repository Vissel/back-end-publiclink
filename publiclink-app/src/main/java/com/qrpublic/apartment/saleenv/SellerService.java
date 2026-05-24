package com.qrpublic.apartment.saleenv;

import com.qrpublic.apartment.requestmodel.Pagination;
import com.qrpublic.apartment.saleenv.request.ListSellerRequestsRequest;
import com.qrpublic.apartment.saleenv.response.ListSellerRequestResponse;
import com.qrpublic.apartment.template.model.Result;
import com.qrpublic.apartment.user.request.GetSellerRequest;
import com.qrpublic.apartment.user.response.GetUserResponse;

public interface SellerService {
    Result<ListSellerRequestResponse> listRequestEnvironment(Pagination<ListSellerRequestsRequest> listRequestEnvironmentRequest);

    Result<GetUserResponse> getUserInfo(GetSellerRequest getSellerRequest);
}
