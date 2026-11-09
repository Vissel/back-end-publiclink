package com.qrpublic.apartment.core.service;

import com.qrpublic.apartment.core.model.TransferInfoModel;
import com.qrpublic.apartment.entity.TransferInfo;
import com.qrpublic.apartment.entity.User;
import com.qrpublic.apartment.exception.ResourceNotFoundException;
import com.qrpublic.apartment.exception.ValidationException;
import com.qrpublic.apartment.repository.TransferInfoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Core layer for transfer info (bank / e-wallet accounts) shared by admin and
 * seller owners. Rows are owned by username because administrators live in the
 * user service, not in publiclink-db.user. Default switching happens inside one
 * transaction so a user never ends up with two default rows.
 */
@Slf4j
@Service
public class CoreTransferInfoService {

    private static final String OWNER_TYPE_ADMIN = "ADMIN";

    @Autowired
    private TransferInfoRepository transferInfoRepository;

    @Autowired
    private CoreUserService coreUserService;

    @Transactional(readOnly = true)
    public List<TransferInfo> findByOwnerAndStatus(String ownerUsername, String status) {
        return transferInfoRepository.findByOwnerAndStatus(ownerUsername, status);
    }

    @Transactional(readOnly = true)
    public List<TransferInfo> findActiveByOwner(String ownerUsername) {
        return transferInfoRepository.findActiveByOwner(ownerUsername);
    }

    @Transactional(readOnly = true)
    public TransferInfo getByCode(String transferCode) {
        return transferInfoRepository.findByTransferCode(transferCode)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer info not found: " + transferCode));
    }

    /**
     * Create a row for the caller. The very first ACTIVE row of an owner
     * becomes the default.
     *
     * @param ownerTypeHint role the caller declared; only used as a fallback
     *                      when the owner has no publiclink user row yet
     */
    @Transactional
    public TransferInfo create(String ownerUsername, String ownerTypeHint, String transferIssuer,
            String issuerFullName, String type, String transferNumber, String accountName, String qrImage,
            String defaultMessage, boolean setDefault) {
        String ownerType = resolveOwnerType(ownerUsername, ownerTypeHint);
        boolean isFirst = transferInfoRepository.findActiveByOwner(ownerUsername).isEmpty();
        if (setDefault || isFirst) {
            transferInfoRepository.clearDefaultByOwner(ownerUsername);
        }

        TransferInfo info = new TransferInfo();
        info.setTransferCode(UUID.randomUUID().toString());
        info.setOwnerUsername(ownerUsername);
        info.setOwnerType(ownerType);
        info.setTransferIssuer(transferIssuer);
        info.setIssuerFullName(issuerFullName);
        info.setType(type);
        info.setTransferNumber(transferNumber);
        info.setAccountName(accountName);
        info.setQrImage(qrImage);
        info.setDefaultMessage(defaultMessage);
        info.setStatus("ACTIVE");
        info.setDefault(setDefault || isFirst);
        log.info("Created {} transfer info [{}] for owner {}", ownerType, transferIssuer, ownerUsername);
        return transferInfoRepository.save(info);
    }

    @Transactional
    public TransferInfo setDefault(String ownerUsername, String transferCode) {
        TransferInfo info = getByCode(transferCode);
        assertOwnedBy(ownerUsername, info);
        transferInfoRepository.clearDefaultByOwner(ownerUsername);
        info.setDefault(true);
        return transferInfoRepository.save(info);
    }

    @Transactional
    public void deactivate(String ownerUsername, String transferCode) {
        TransferInfo info = getByCode(transferCode);
        assertOwnedBy(ownerUsername, info);
        info.setStatus("DEACTIVE");
        info.setDefault(false);
        transferInfoRepository.save(info);
    }

    /**
     * Sellers are mirrored in publiclink-db.user, admins are not, so a missing
     * user row means the caller is an admin.
     */
    private String resolveOwnerType(String ownerUsername, String ownerTypeHint) {
        Optional<User> owner = coreUserService.findUserEntityByUsername(ownerUsername);
        if (owner.isPresent() && owner.get().getType() != null && !owner.get().getType().isBlank()) {
            return owner.get().getType().toUpperCase();
        }
        if (ownerTypeHint != null && !ownerTypeHint.isBlank()) {
            return ownerTypeHint.trim().toUpperCase();
        }
        return OWNER_TYPE_ADMIN;
    }

    private void assertOwnedBy(String ownerUsername, TransferInfo info) {
        if (!ownerUsername.equals(info.getOwnerUsername())) {
            throw new ValidationException("Transfer info does not belong to the caller.");
        }
    }

    public TransferInfoModel toModel(TransferInfo info) {
        return TransferInfoModel.builder()
                .transferCode(info.getTransferCode())
                .ownerType(info.getOwnerType())
                .transferIssuer(info.getTransferIssuer())
                .issuerFullName(info.getIssuerFullName())
                .type(info.getType())
                .transferNumber(info.getTransferNumber())
                .accountName(info.getAccountName())
                .qrImage(info.getQrImage())
                .defaultMessage(info.getDefaultMessage())
                .status(info.getStatus())
                .isDefault(info.isDefault())
                .build();
    }
}
