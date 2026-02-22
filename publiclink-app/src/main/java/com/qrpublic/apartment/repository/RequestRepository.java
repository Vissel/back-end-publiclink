package com.qrpublic.apartment.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.qrpublic.apartment.entity.Request;

public interface RequestRepository extends JpaRepository<Request, Long> {

}
