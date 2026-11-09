package com.qrpublic.apartment.service;

import com.qrpublic.apartment.domain.transferinfo.TransferInfoDomain;
import com.qrpublic.apartment.template.model.Result;
import com.qrpublic.apartment.template.service.ProcessCallback;
import com.qrpublic.apartment.template.service.PublicLinkServiceTemplate;
import com.qrpublic.apartment.transferinfo.request.CreateTransferInfoRequest;
import com.qrpublic.apartment.transferinfo.response.TransferInfoListResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

/**
 * Transfer info operations shared by admin and seller. The owner is always the
 * authenticated caller (username from the gateway header); role separation
 * happens in the core layer from the user record.
 */
@Slf4j
@Service
public class TransferInfoService {

    @Autowired
    private TransferInfoDomain transferInfoDomain;

    @Autowired
    private PublicLinkServiceTemplate publicLinkServiceTemplate;

    public Result<TransferInfoListResponse> list(String username, String status) {
        return publicLinkServiceTemplate.execute(new ProcessCallback<Void, TransferInfoListResponse>() {
            @Override
            public Void getRequest() {
                return null;
            }

            @Override
            public void preProcess(Void request) {
                Assert.hasText(username, "Caller identity is required.");
            }

            @Override
            public TransferInfoListResponse process() {
                return transferInfoDomain.list(username.trim(), status);
            }
        });
    }

    public Result<TransferInfoListResponse.TransferItem> create(String username,
            CreateTransferInfoRequest request) {
        return publicLinkServiceTemplate.execute(
                new ProcessCallback<CreateTransferInfoRequest, TransferInfoListResponse.TransferItem>() {
            @Override
            public CreateTransferInfoRequest getRequest() {
                return request;
            }

            @Override
            public void preProcess(CreateTransferInfoRequest req) {
                Assert.hasText(username, "Caller identity is required.");
                Assert.hasText(req.getTransferIssuer(), "Transfer issuer is required.");
                Assert.hasText(req.getTransferNumber(), "Transfer number is required.");
            }

            @Override
            public TransferInfoListResponse.TransferItem process() {
                return transferInfoDomain.create(username.trim(), request);
            }
        });
    }

    public Result<Void> setDefault(String username, String transferCode) {
        return publicLinkServiceTemplate.execute(new ProcessCallback<Void, Void>() {
            @Override
            public Void getRequest() {
                return null;
            }

            @Override
            public void preProcess(Void request) {
                Assert.hasText(username, "Caller identity is required.");
                Assert.hasText(transferCode, "Transfer code is required.");
            }

            @Override
            public Void process() {
                transferInfoDomain.setDefault(username.trim(), transferCode.trim());
                return null;
            }
        });
    }

    public Result<Void> remove(String username, String transferCode) {
        return publicLinkServiceTemplate.execute(new ProcessCallback<Void, Void>() {
            @Override
            public Void getRequest() {
                return null;
            }

            @Override
            public void preProcess(Void request) {
                Assert.hasText(username, "Caller identity is required.");
                Assert.hasText(transferCode, "Transfer code is required.");
            }

            @Override
            public Void process() {
                transferInfoDomain.remove(username.trim(), transferCode.trim());
                return null;
            }
        });
    }
}
