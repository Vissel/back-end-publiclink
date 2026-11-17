package com.qrpublic.apartment.repository;

import com.qrpublic.apartment.entity.TransferInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransferInfoRepository extends JpaRepository<TransferInfo, Long> {

        Optional<TransferInfo> findByTransferCode(String transferCode);

        @Query("SELECT t FROM TransferInfo t " +
                        "WHERE t.ownerUsername = :ownerUsername AND t.status = :status " +
                        "ORDER BY t.isDefault DESC, t.createdAt DESC")
        List<TransferInfo> findByOwnerAndStatus(@Param("ownerUsername") String ownerUsername,
                        @Param("status") String status);

        @Query("SELECT t FROM TransferInfo t " +
                        "WHERE t.ownerUsername = :ownerUsername AND t.status = 'ACTIVE' " +
                        "ORDER BY t.isDefault DESC, t.createdAt DESC")
        List<TransferInfo> findActiveByOwner(@Param("ownerUsername") String ownerUsername);

        @Modifying
        @Query("UPDATE TransferInfo t SET t.isDefault = false " +
                        "WHERE t.ownerUsername = :ownerUsername AND t.status = 'ACTIVE' AND t.isDefault = true")
        int clearDefaultByOwner(@Param("ownerUsername") String ownerUsername);
}
