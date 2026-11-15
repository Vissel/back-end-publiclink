package com.qrpublic.apartment.domain.transferinfo;

import com.qrpublic.apartment.core.service.CoreTransferInfoService;
import com.qrpublic.apartment.entity.SaleEnvironment;
import com.qrpublic.apartment.entity.TransferInfo;
import com.qrpublic.apartment.exception.ApplicationException;
import com.qrpublic.apartment.exception.ResourceNotFoundException;
import com.qrpublic.apartment.repository.SaleEnvironmentRepository;
import com.qrpublic.apartment.repository.TransferInfoRepository;
import com.qrpublic.apartment.saleenv.response.EnvTransferInfoDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * The transfer account a sale page shows so buyers can pay. The seller of the
 * environment attaches one of their own accounts; an administrator attaches
 * one of the admin's own. Either side may detach. The page resolves the code
 * at read time, so an account the owner has since removed just disappears.
 */
@Slf4j
@Component
public class EnvironmentTransferDomain {

    private static final String STATUS_ACTIVE = "ACTIVE";

    @Autowired
    private SaleEnvironmentRepository saleEnvironmentRepository;

    @Autowired
    private TransferInfoRepository transferInfoRepository;

    @Autowired
    private CoreTransferInfoService coreTransferInfoService;

    /**
     * Seller side: the caller must own the environment.
     */
    @Transactional
    public EnvTransferInfoDTO updateForSeller(String requestUuid, String transferCode, String sellerUsername) {
        if (!StringUtils.hasText(sellerUsername)) {
            throw new ApplicationException("Authentication is required.", 401);
        }
        SaleEnvironment env = findEnvironment(requestUuid);
        String owner = env.getRequest() != null ? env.getRequest().getSellerName() : null;
        if (!sellerUsername.trim().equals(owner)) {
            throw new ApplicationException("This environment belongs to another seller.", 403);
        }
        return apply(env, transferCode, sellerUsername.trim());
    }

    /**
     * Admin side: role is enforced by the gateway, any environment may be
     * edited, but the account still has to be the admin's own.
     */
    @Transactional
    public EnvTransferInfoDTO updateForAdmin(String requestUuid, String transferCode, String adminUsername) {
        if (!StringUtils.hasText(adminUsername)) {
            throw new ApplicationException("Authentication is required.", 401);
        }
        return apply(findEnvironment(requestUuid), transferCode, adminUsername.trim());
    }

    /**
     * What the public page shows: null when nothing is attached or the
     * attached account is no longer ACTIVE.
     */
    @Transactional(readOnly = true)
    public EnvTransferInfoDTO resolve(SaleEnvironment env) {
        if (env == null || !StringUtils.hasText(env.getTransferCode())) {
            return null;
        }
        return transferInfoRepository.findByTransferCode(env.getTransferCode())
                .filter(info -> STATUS_ACTIVE.equals(info.getStatus()))
                .map(this::toDTO)
                .orElse(null);
    }

    private EnvTransferInfoDTO apply(SaleEnvironment env, String transferCode, String caller) {
        if (!StringUtils.hasText(transferCode)) {
            env.setTransferCode(null);
            saleEnvironmentRepository.save(env);
            log.info("[{}] detached the transfer account from environment [{}]", caller, env.getEnvId());
            return null;
        }
        TransferInfo info = coreTransferInfoService.getByCode(transferCode.trim());
        if (!caller.equals(info.getOwnerUsername())) {
            throw new ApplicationException("Transfer info does not belong to the caller.", 403);
        }
        if (!STATUS_ACTIVE.equals(info.getStatus())) {
            throw new ApplicationException("This transfer account has been removed.", 400);
        }
        env.setTransferCode(info.getTransferCode());
        saleEnvironmentRepository.save(env);
        log.info("[{}] attached transfer account [{}] to environment [{}]", caller, info.getTransferCode(),
                env.getEnvId());
        return toDTO(info);
    }

    private SaleEnvironment findEnvironment(String requestUuid) {
        if (!StringUtils.hasText(requestUuid)) {
            throw new ApplicationException("requestUuid is required.", 400);
        }
        final String uuid = requestUuid.trim();
        return saleEnvironmentRepository.findByRequestReqUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Environment not found: " + uuid));
    }

    private EnvTransferInfoDTO toDTO(TransferInfo info) {
        EnvTransferInfoDTO dto = new EnvTransferInfoDTO();
        dto.setTransferCode(info.getTransferCode());
        dto.setTransferIssuer(info.getTransferIssuer());
        dto.setIssuerFullName(info.getIssuerFullName());
        dto.setType(info.getType());
        dto.setTransferNumber(info.getTransferNumber());
        dto.setAccountName(info.getAccountName());
        dto.setQrImage(info.getQrImage());
        dto.setDefaultMessage(info.getDefaultMessage());
        return dto;
    }
}
