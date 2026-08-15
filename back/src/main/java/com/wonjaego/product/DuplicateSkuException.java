package com.wonjaego.product;

public class DuplicateSkuException extends RuntimeException {

    public DuplicateSkuException(String sku) {
        super("이미 사용 중인 SKU입니다: " + sku);
    }
}
