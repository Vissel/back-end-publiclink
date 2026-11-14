package com.qrpublic.apartment.domain.transferinfo;

import com.qrpublic.apartment.core.model.TransferInfoModel;
import com.qrpublic.apartment.core.service.CoreTransferInfoService;
import com.qrpublic.apartment.transferinfo.request.CreateTransferInfoRequest;
import com.qrpublic.apartment.transferinfo.response.TransferInfoListResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Transfer info business logic, shared by admin and seller owners. Owner
 * identity is resolved from the caller server-side, so the same component and
 * APIs serve both roles. Only transferCode travels on the wire.
 */
@Slf4j
@Component
public class TransferInfoDomain {

    @Autowired
    private CoreTransferInfoService coreTransferInfoService;

    @Transactional(readOnly = true)
    public TransferInfoListResponse list(String username, String status) {
        String effectiveStatus = (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status))
                ? "ACTIVE"
                : status.toUpperCase();
        List<TransferInfoModel> models = coreTransferInfoService
                .findByOwnerAndStatus(username, effectiveStatus).stream()
                .map(coreTransferInfoService::toModel)
                .toList();
        TransferInfoListResponse response = new TransferInfoListResponse();
        response.setItems(models.stream().map(this::toItem).toList());
        return response;
    }

    @Transactional
    public TransferInfoListResponse.TransferItem create(String username, CreateTransferInfoRequest request) {
        return toItem(coreTransferInfoService.toModel(coreTransferInfoService.create(
                username,
                request.getOwnerHint(),
                request.getTransferIssuer(), request.getIssuerFullName(),
                request.getType(), request.getTransferNumber(), request.getBankBin(), request.getAccountName(),
                request.getQrImage(), request.getDefaultMessage(), request.isSetDefault())));
    }

    @Transactional
    public void setDefault(String username, String transferCode) {
        coreTransferInfoService.setDefault(username, transferCode);
    }

    @Transactional
    public void remove(String username, String transferCode) {
        coreTransferInfoService.deactivate(username, transferCode);
    }

    private TransferInfoListResponse.TransferItem toItem(TransferInfoModel model) {
        TransferInfoListResponse.TransferItem item = new TransferInfoListResponse.TransferItem();
        item.setTransferCode(model.getTransferCode());
        item.setTransferIssuer(model.getTransferIssuer());
        item.setIssuerFullName(model.getIssuerFullName());
        item.setType(model.getType());
        item.setTransferNumber(model.getTransferNumber());
        item.setAccountName(model.getAccountName());
        item.setQrImage(model.getQrImage());
        item.setDefaultMessage(model.getDefaultMessage());
        item.setStatus(model.getStatus());
        item.setDefault(model.isDefault());
        return item;
    }
}
