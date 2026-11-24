package com.qrpublic.apartment.requestmodel;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Picture payload for the seller product-update flow. Images are stored as
 * base64 data; unlike {@link PictureDTO} there is no link field, because the
 * application never writes a URL and the update flow must not carry one.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductPictureRequest {

	private String title;

	private String data;
}
