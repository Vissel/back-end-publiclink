package com.qrpublic.apartment.repository;

import com.qrpublic.apartment.entity.ProcessMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessMessageRepository extends JpaRepository<ProcessMessage, String> {

}
