package com.wonjaego.product;

import java.util.List;

// matchedSku is set only when this product was reached via a barcode/SKU lookup (not a
// name search) — the "선택된 상품" card shows the scanned barcode only in that case.
// imageUrl follows Product.resolveImageUrl() — same rule the product list screen uses.
public record ProductStockEntryResponse(Long productId, String productName, String imageUrl,
                                         String matchedSku, List<StockEntryVariant> variants) {
}
