package com.qrpublic.apartment.service;

import com.qrpublic.apartment.constant.CommonConstant;
import com.qrpublic.apartment.core.model.RequestModel;
import com.qrpublic.apartment.core.service.CoreRequestService;
import com.qrpublic.apartment.entity.*;
import com.qrpublic.apartment.model.SellerDTO;
import com.qrpublic.apartment.repository.RequestRepository;
import com.qrpublic.apartment.requestmodel.PictureDTO;
import com.qrpublic.apartment.requestmodel.ProductDTO;
import com.qrpublic.apartment.requestmodel.PubUserRequest;
import com.qrpublic.apartment.requestmodel.RequestDTO;
import com.qrpublic.apartment.saleenv.SaleEnvironmentService;
import com.qrpublic.apartment.saleenv.request.CreateRequestIdRequest;
import com.qrpublic.apartment.saleenv.response.CreateRequestIdResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RequestServiceImpl implements RequestService {
    @Autowired
    private RequestRepository requestRepo;
    @Autowired
    private UserService userService;
    @Autowired
    private SaleEnvironmentService envService;

    @Autowired
    CoreRequestService coreRequestService;

    @Override
    public RequestDTO createTempRequestDTO(SellerDTO seller) {
        log.info("Creating seller:{}", seller.getUsername());
        RequestDTO requestDTO = null;
        // List<User> listSeller = userRepo.findByNameAndLink(seller.getUsername(),
        // seller.getLink());
        // handle 1 seller as currently
        // User requestSeller;
        // if (!listSeller.isEmpty()) {
        // requestSeller = listSeller.get(0);
        // } else {
        // requestSeller = createNewSeller(seller);
        // }
        // created_at get from DB as
        // UserDetails u = UserUtils.getCurrentUser();
        // if (u != null
        // && u.getAuthorities().stream().anyMatch(auth ->
        // auth.getAuthority().equals(RoleEnum.ADMIN))) {
        // User admin = userRepo.findByUserName(u.getUsername()).orElseThrow(
        // () -> new UsernameNotFoundException("User not found with username: " +
        // u.getUsername()));
        //
        // // response DTO
        // requestDTO = new RequestDTO(seller, CommonConstant.EMPTY, admin, false, new
        // ArrayList<>());
        // log.info("Creating seller successfull by {}", u.getUsername());
        // }
        log.info("Creating seller:{}{}", seller.getUsername(), CommonConstant.END);
        return requestDTO;
    }

    @Override
    public String generatePublicLink(SellerDTO sellerDTO) {
        log.info("Generating public link for seller:{}", sellerDTO.getUsername());
        String publicToken = null;

        // create seller account, check if exist
        User seller = userService.createSeller(sellerDTO);
        if (seller != null) {
            // save request, product, img to db
            Request request = new Request();
            request.setSellerName(seller.getUserName());
            String adminUserName = null;
            // UserUtils.getCurrentUser().getUsername();
            User createdBy = userService.findByUserName(adminUserName);
            request.setCreatedBy(createdBy);

            // product infor
            if (sellerDTO.getProductName() != null && !sellerDTO.getProductName().isBlank()) {
                Product product = new Product();
                product.setProductName(sellerDTO.getProductName());
                product.setRequest(request);
                request.setProducts(Arrays.asList(product));
            }
            // save others
            long reqId = saveRequest(request).getReqId();
            log.debug("Created new request:{}", reqId);
            // create sale_env and generate public link
            publicToken = envService.createSaleEnvironment(request).getPublicLink();
        }
        log.info("Generating public link for seller:{}{}", sellerDTO.getUsername(), CommonConstant.END);
        return publicToken;
    }

    public String generatePublicLink(RequestDTO requestDTO) {
        log.info("Generating public link for seller:{}", requestDTO.getSeller().getUsername());
        String publicLink = null;

        // create seller account, check if exist
        User seller = userService.createSeller(requestDTO.getSeller());
        if (seller != null) {
            // save request, product, img to db
            Request request = new Request();
            request.setSellerName(seller.getUserName());
            request.setDescription(requestDTO.getDescription());
            request.setCreatedBy(requestDTO.getCreatedBy()); // debug check admin
            request.setAuthenticated(requestDTO.isAuthenticated());
            // save request 1st
            // saveRequest(request);
            List<Product> products = requestDTO.getProducts().stream().map(p -> createProduct(p, request))
                    .collect(Collectors.toList());
            request.setProducts(products);
            // save others
            long reqId = saveRequest(request).getReqId();
            log.debug("Created new request:{}", reqId);
            // create sale_env and generate public link
            publicLink = envService.createSaleEnvironment(request).getPublicLink();
        }
        log.info("Generating public link for seller:{}{}", requestDTO.getSeller().getUsername(), CommonConstant.END);
        return publicLink;
    }

    @Override
    public Request saveRequest(Request request) {
        // taking care for update cases.
        return requestRepo.save(request);
    }

    private Product createProduct(ProductDTO p, Request request) {
        Product product = new Product();
        product.setProductName(p.getProductName());
        product.setAmount(p.getAmount());
        product.setUnit(p.getUnit());
        product.setPrice(p.getPrice());
        product.setTotal_amount(p.getTotal_amount());
        product.setRequest(request);
        // set images

        List<ProductPictureMap> prodPicMap = createProPicMap(p.getListPicProMap(), product);
        product.setListPicProMap(prodPicMap);
        return product;
    }

    private List<ProductPictureMap> createProPicMap(List<PictureDTO> picDTOs, Product product) {
        // 1 pictureDTO - 1 ProductPictureMap
        return picDTOs.stream().map(pic -> new ProductPictureMap(product, new Picture(pic.getLink(), pic.getTitle())))
                .collect(Collectors.toList());
    }

    @Override
    public String generateSellerAuthLink(User seller, long reqId) {
        // gen link for seller set user name password
        String urlparam = "id=" + seller.getUserId() + "&username=" + seller.getUserName() + "&reqid=" + reqId;
        return "public/register?" + Base64.getUrlEncoder().encodeToString(urlparam.getBytes());
    }

    @Override
    public Request saveAuthenticatedRequest(long requestId) {
        Request request = requestRepo.findById(requestId).orElse(null);
        if (request != null) {
            request.setAuthenticated(true);
            return requestRepo.save(request);
        }
        return null;
    }

    @Override
    public Mono<CreateRequestIdResponse> generateRequestId(CreateRequestIdRequest request) {
        return Mono.fromCallable(() -> {
            PubUserRequest sellerReq = request.getSellerRequest();
            SellerDTO sellerDTO = new SellerDTO();
            sellerDTO.setUsername(sellerReq.getUsername());
            sellerDTO.setLink(sellerReq.getLink());
            sellerDTO.setName(sellerReq.getName());
            sellerDTO.setPrice(sellerReq.getPrice());

            return convertModelToCreateRequestIdResponse(coreRequestService.generateRequestForSeller(sellerDTO));
        });
    }

    private CreateRequestIdResponse convertModelToCreateRequestIdResponse(RequestModel requestModel) {
        CreateRequestIdResponse response = new CreateRequestIdResponse();
        response.setRequestUuid(requestModel.getRequestUuid());
        response.setCreatedAt(requestModel.getCreatedAt());
        if (requestModel.getSeller() != null) {
            response.setSellerName(requestModel.getSeller().getUsername());
        }
        return response;
    }

}
