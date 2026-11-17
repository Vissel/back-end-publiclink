package com.qrpublic.apartment.saleenv;

import com.qrpublic.apartment.requestmodel.Pagination;
import com.qrpublic.apartment.requestmodel.SaleEnvDTO;
import com.qrpublic.apartment.saleenv.request.ListSellerRequestsRequest;
import com.qrpublic.apartment.saleenv.request.UpdateEnvironmentTransferRequest;
import com.qrpublic.apartment.saleenv.request.UpdateEnvironmentWindowRequest;
import com.qrpublic.apartment.saleenv.response.EnvTransferInfoDTO;
import com.qrpublic.apartment.saleenv.response.ListSellerRequestResponse;
import com.qrpublic.apartment.template.model.Result;
import com.qrpublic.apartment.saleenv.request.ExportReportRequest;
import com.qrpublic.apartment.user.request.GetSellerRequest;
import com.qrpublic.apartment.user.response.GetUserResponse;

public interface SellerService {
    Result<ListSellerRequestResponse> listRequestEnvironment(
            Pagination<ListSellerRequestsRequest> listRequestEnvironmentRequest);

    Result<GetUserResponse> getUserInfo(GetSellerRequest getSellerRequest);

    byte[] exportReport(ExportReportRequest request);

    /**
     * Seller edit of the sale window: move the closing time earlier, close the
     * sale immediately, or extend it while the paid plan covers it. Throws
     * ApplicationException (turned into a response carrying its message by the
     * global handler) instead of returning a failed Result, whose body is
     * dropped by ResponseEntityConvertor.
     */
    SaleEnvDTO updateEnvironmentWindow(UpdateEnvironmentWindowRequest request, String sellerUsername);

    /**
     * Seller attaches one of their own transfer accounts to the sale page (or
     * detaches it with a blank code). Returns what the page will now show,
     * null after a detach. Ownership of the environment is checked.
     */
    EnvTransferInfoDTO updateEnvironmentTransferInfo(UpdateEnvironmentTransferRequest request, String sellerUsername);
}
