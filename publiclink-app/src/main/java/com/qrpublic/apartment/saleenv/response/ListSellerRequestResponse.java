package com.qrpublic.apartment.saleenv.response;

import com.qrpublic.apartment.requestmodel.SaleEnvDTO;
import lombok.Data;

import java.util.List;

@Data
public class ListSellerRequestResponse {
    private int total;
    private List<SaleEnvDTO> listSaleEnv;
}
