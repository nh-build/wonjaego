package com.wonjaego.product;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ProductVariantNotFoundException extends RuntimeException {

    public ProductVariantNotFoundException(Long id) {
        super("상품 변형을 찾을 수 없습니다: " + id);
    }
}
