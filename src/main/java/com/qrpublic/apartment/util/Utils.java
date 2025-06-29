package com.qrpublic.apartment.util;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import com.qrpublic.apartment.constant.CommonConstant;
import com.qrpublic.apartment.entity.Order;
import com.qrpublic.apartment.entity.SaleEnvironment;
import com.qrpublic.apartment.requestmodel.OrderDTO;
import com.qrpublic.apartment.requestmodel.SaleEnvDTO;

public class Utils {
	public static String subDate(Timestamp createdAt) {
		// "2025-06-12T19:26:03.000+00:00"

		return createdAt.toString().substring(0, createdAt.toString().indexOf(CommonConstant.DOT))
				.replace(CommonConstant.T_STR, CommonConstant.SPACE);
	}

	public static String formatTimeStamp(Timestamp timestamp) {
		if (timestamp == null) {
			return CommonConstant.EMPTY;
		}
		Instant instant = timestamp.toInstant();
		ZoneId zoneId = ZoneId.of("UTC");
		ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(instant, zoneId);

		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
		return zonedDateTime.format(formatter);
	}

	public static SaleEnvDTO createEnvDTO(SaleEnvironment env) {
		String productName = CommonConstant.EMPTY;
		if (!env.getRequest().getProducts().isEmpty()) {
			productName = env.getRequest().getProducts().getFirst().getProductName();
		}
		List<OrderDTO> orders = createListOrderDTO(env.getListOrder());

		return new SaleEnvDTO(Utils.formatTimeStamp(env.getCreatedAt()), env.getRequest().getSellerId().getUserName(),
				env.getRequest().getSellerId().getLink(), productName, env.getPublicLink(),
				env.getRequest().getCreatedBy().getName(), env.isState(), orders);
	}

	private static List<OrderDTO> createListOrderDTO(List<Order> listOrder) {
		List<OrderDTO> orderDTOs = new ArrayList<>();
		if (listOrder != null && !listOrder.isEmpty()) {
			String publicLink = listOrder.get(0).getSaleEnvironment().getPublicLink();
			listOrder.stream().forEach(o -> orderDTOs.add(createOrderDTO(o, o.getOrderedAt().getTime(), publicLink)));
		}
		return orderDTOs;
	}

	public static OrderDTO createOrderDTO(Order order, long time, String publicLink) {
		SimpleDateFormat formater = new SimpleDateFormat(CommonConstant.DATETIME_PATTERN);
		formater.setTimeZone(TimeZone.getTimeZone("UTC"));
		String orderTime = formater.format(new Date(time));
		return new OrderDTO(order.getOrderId(), orderTime, order.getBuyerName(), publicLink, order.isDelivered(),
				order.isGetMoney(), order.getSellerNote(), order.getAmount(), order.getUnit(), order.getNote());

	}

	public static boolean isValidStr(String string) {
		return string != null && !string.isBlank();
	}
}
