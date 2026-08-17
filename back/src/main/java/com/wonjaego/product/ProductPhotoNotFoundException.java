package com.wonjaego.product;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ProductPhotoNotFoundException extends RuntimeException {

    public ProductPhotoNotFoundException(Long productId) {
        super("상품 사진을 찾을 수 없습니다: " + productId);
    }
}
