package com.qrpublic.apartment.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.stereotype.Service;

import com.qrpublic.apartment.constant.CommonConstant;
import com.qrpublic.apartment.entity.Request;
import com.qrpublic.apartment.entity.SaleEnvironment;
import com.qrpublic.apartment.repository.SaleEnvironmentRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SaleEnvironmentImpl implements SaleEnvironmentService {

	@Autowired
	private SaleEnvironmentRepository repo;

	@Autowired
	LinkService linkService;

	@Override
	public SaleEnvironment createSaleEnvironment(Request request) {
		log.info("Creating sale environment.");
		SaleEnvironment env = repo.save(new SaleEnvironment(request));
		if (env.getEnvId() != null && !env.getEnvId().isBlank()) {
			final String publicLink = generatePublicLink(env, request);
			log.info("Creating sale environment. Public link:{}", publicLink);
			env.setPublicLink(publicLink);
		}
		log.info("Creating sale environment. Update DB and done.{}", CommonConstant.END);
		return repo.save(env);
	}

	private String generatePublicLink(SaleEnvironment env, Request request) {

		return linkService.generateSecureUrl(env.getEnvId(), request.getReqId());
	}

	@Override
	public List<SaleEnvironment> getAllEnvironment() {
		return repo.findAll(Sort.by(Order.desc("createdAt")));
	}

	@Override
	public SaleEnvironment getEnvironmentByPublicLink(String publicLink) {
		return repo.findByPublicLink(publicLink).orElse(null);
	}

	@Override
	public String getPublicLinkBy(Request request) {
		return repo.findByRequest(request).get().getPublicLink();
	}
}
