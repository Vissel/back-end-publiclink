package com.qrpublic.apartment.databackup.repository;

import com.qrpublic.apartment.databackup.entity.TriggerEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TriggerRepository extends JpaRepository<TriggerEntity, Long> {
    Page<TriggerEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
