package com.qrpublic.apartment.requestmodel;

import com.qrpublic.apartment.entity.Request;
import com.qrpublic.apartment.entity.User;
import com.qrpublic.apartment.model.SellerDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RequestDTO extends AuthToken {
    private SellerDTO seller;

    private String description;

    private User createdBy;

    private boolean authenticated;

    private List<ProductDTO> products;

    private String reqUUID;

    /**
     * considering unuse
     *
     * @param request
     */
    public RequestDTO(Request request) {
        this.seller = new SellerDTO(request.getSellerId());
        this.description = request.getDescription();
        this.createdBy = request.getCreatedBy();
        this.products = new ArrayList<>();
    }
}
