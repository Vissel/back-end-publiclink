package com.qrpublic.apartment.saleenv;

import com.qrpublic.apartment.entity.Request;
import com.qrpublic.apartment.entity.SaleEnvironment;
import com.qrpublic.apartment.requestmodel.SaleEnvDTO;
import com.qrpublic.apartment.saleenv.request.CreateEnvironmentRequest;

import java.util.List;

public interface SaleEnvironmentService {
    public SaleEnvironment createSaleEnvironment(Request request);

    /**
     * Create a sale environment for a given request and return the public link and other details.
     *
     * @param request
     * @return
     */
    SaleEnvDTO createSaleEnvironment(CreateEnvironmentRequest request);

    public List<SaleEnvDTO> getAllEnvironment();

    public SaleEnvironment getEnvironmentByPublicLink(String publicLink);

    public String getPublicLinkBy(Request request);


}
