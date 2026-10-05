package com.qrpublic.apartment.plan;

import com.qrpublic.apartment.core.model.RequestModel;
import com.qrpublic.apartment.model.PriceModel;
import com.qrpublic.apartment.plan.request.CreatePlanRequest;
import com.qrpublic.apartment.plan.response.CreatePlanResponse;
import com.qrpublic.apartment.service.PriceService;
import com.qrpublic.apartment.service.RequestService;
import com.qrpublic.apartment.requestmodel.RequestDTO;
import com.qrpublic.apartment.model.SellerDTO;
import com.qrpublic.apartment.requestmodel.ProductDTO;
import com.qrpublic.apartment.requestmodel.PictureDTO;
import com.qrpublic.apartment.requestmodel.PubUserRequest;
import com.qrpublic.apartment.requestmodel.PriceRequest;
import com.qrpublic.apartment.product.request.CreateProductRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PlanService {
    @Autowired
    RequestService requestService;

    @Autowired
    PriceService priceService;

    @Transactional
    public CreatePlanResponse createPricingPlanForSeller(CreatePlanRequest createPlanRequest) {
        RequestDTO requestDTO = convertCreatePlanRequestToRequestDTO(createPlanRequest);
        PriceModel priceModel = convertToPriceModel(createPlanRequest.getSellerRequest().getPrice(), null);
        RequestModel requestModel = requestService.saveRequest(requestDTO, priceModel);
        
        priceModel.setRequestModel(requestModel);
        priceService.createPricingRecord(priceModel);

        CreatePlanResponse response = new CreatePlanResponse();
        response.setRequestUuid(requestModel.getRequestUuid());
        if (requestModel.getSeller() != null) {
            response.setSellerUsername(requestModel.getSeller().getUsername());
        }
        response.setStatus("SUCCESS");
        return response;
    }

    private PriceModel convertToPriceModel(PriceRequest priceReq, RequestModel requestModel) {
        PriceModel priceModel = new PriceModel();
        priceModel.setRequestModel(requestModel);
        priceModel.setDurationHours(priceReq.getDurationHours());
        priceModel.setAmount(priceReq.getPriceAmount());
        priceModel.setCurrency(priceReq.getCurrency() != null ? priceReq.getCurrency() : "VND");
        return priceModel;
    }

    private RequestDTO convertCreatePlanRequestToRequestDTO(CreatePlanRequest createPlanRequest) {
       RequestDTO requestDTO = new RequestDTO();
        if (createPlanRequest.getSellerRequest() != null) {
            PubUserRequest sellerReq = createPlanRequest.getSellerRequest();
            SellerDTO sellerDTO = new SellerDTO();
            sellerDTO.setUsername(sellerReq.getUsername());
            sellerDTO.setLink(sellerReq.getLink());
            sellerDTO.setName(sellerReq.getName());
            sellerDTO.setPrice(sellerReq.getPrice());
            requestDTO.setSeller(sellerDTO);
        }
        
        if (createPlanRequest.getProductRequest() != null) {
            CreateProductRequest prodReq = createPlanRequest.getProductRequest();
            List<PictureDTO> pictures = List.of();
            if (prodReq.getImageDataList() != null) {
                pictures = prodReq.getImageDataList().stream().map(img -> {
                    PictureDTO pic = new PictureDTO();
                    pic.setData(img);
                    return pic;
                }).collect(Collectors.toList());
            }
            ProductDTO productDTO = new ProductDTO(
                prodReq.getName(),
                prodReq.getQuantity(),
                "VND",
                prodReq.getPrice(),
                (int) (prodReq.getQuantity() * prodReq.getPrice()),
                pictures
            );
            requestDTO.setProducts(List.of(productDTO));
        }
        if ()
        return requestDTO;
    }
}
