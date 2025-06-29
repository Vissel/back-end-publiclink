package com.qrpublic.apartment.service;

import java.util.List;

import com.qrpublic.apartment.entity.Request;
import com.qrpublic.apartment.entity.SaleEnvironment;

public interface SaleEnvironmentService {
	public SaleEnvironment createSaleEnvironment(Request request);

	public List<SaleEnvironment> getAllEnvironment();

	public SaleEnvironment getEnvironmentByPublicLink(String publicLink);

	public String getPublicLinkBy(Request request);

}
