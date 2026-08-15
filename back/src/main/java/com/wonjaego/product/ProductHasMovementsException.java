package com.wonjaego.product;

public class ProductHasMovementsException extends RuntimeException {

    public ProductHasMovementsException(String productName) {
        super("재고 기록이 있어 삭제할 수 없습니다: " + productName);
    }
}
