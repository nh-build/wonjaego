package com.wonjaego.product;

import com.wonjaego.common.BaseEntity;
import com.wonjaego.member.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "product_variants", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"member_id", "sku"}),
        @UniqueConstraint(columnNames = {"product_id", "external_item_id"})
})
public class ProductVariant extends BaseEntity {

    public static final int DEFAULT_LOW_STOCK_THRESHOLD = 5;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToMany
    @JoinTable(name = "product_variant_option_values",
            joinColumns = @JoinColumn(name = "product_variant_id"),
            inverseJoinColumns = @JoinColumn(name = "option_value_id"))
    private Set<OptionValue> optionValues = new LinkedHashSet<>();

    private String sku;

    @Column(nullable = false)
    private int stockQuantity;

    private Integer lowStockThreshold;

    // Null for a manually-registered variant (ADR 0006). Set for a variant imported via
    // 채널 연동 — the source channel's own item id, used to dedupe re-imports.
    private String externalItemId;

    // ADR 0008 — price lives here, not on Product. Every variant always has one: computed
    // from base price + option-value surcharges at manual registration, or taken directly
    // from the channel's own SKU price on import.
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    public ProductVariant(Product product, Set<OptionValue> optionValues, BigDecimal price) {
        this.member = product.getMember();
        this.product = product;
        this.optionValues = optionValues;
        this.stockQuantity = 0;
        this.price = price;
    }

    public ProductVariant(Product product, Set<OptionValue> optionValues, BigDecimal price, String externalItemId) {
        this.member = product.getMember();
        this.product = product;
        this.optionValues = optionValues;
        this.stockQuantity = 0;
        this.price = price;
        this.externalItemId = externalItemId;
    }

    public void adjustStock(int delta) {
        int newQuantity = this.stockQuantity + delta;
        if (newQuantity < 0) {
            throw new InsufficientStockException(getDisplayName());
        }
        this.stockQuantity = newQuantity;
    }

    public void updateSkuAndThreshold(String sku, Integer lowStockThreshold) {
        this.sku = sku;
        this.lowStockThreshold = lowStockThreshold;
    }

    public void updatePrice(BigDecimal price) {
        this.price = price;
    }

    public int getEffectiveLowStockThreshold() {
        return lowStockThreshold != null ? lowStockThreshold : DEFAULT_LOW_STOCK_THRESHOLD;
    }

    public boolean isLowStock() {
        return stockQuantity <= getEffectiveLowStockThreshold();
    }

    // Option values joined in the order their OptionGroups were created (e.g. "블랙 / S").
    // Empty for a variant with no options (the product itself is the only stock unit).
    public String getOptionLabel() {
        return optionValues.stream()
                .sorted(Comparator.comparing(ov -> ov.getOptionGroup().getId()))
                .map(OptionValue::getValue)
                .collect(Collectors.joining(" / "));
    }

    public String getDisplayName() {
        String optionLabel = getOptionLabel();
        return optionLabel.isEmpty() ? product.getName() : product.getName() + " / " + optionLabel;
    }

    // ADR 0008 — Product no longer stores its own price; this is the "대표가" shown in list/
    // detail screens, computed on demand from the product's variants rather than kept in sync
    // as a stored duplicate. The lowest variant price, with a "~" suffix when variants differ,
    // e.g. "19,000원~" (spec-mandated format, ADR 0008).
    public static String formatPriceRange(List<ProductVariant> variants) {
        if (variants.isEmpty()) {
            return "-";
        }
        BigDecimal min = variants.stream().map(ProductVariant::getPrice).min(Comparator.naturalOrder()).orElseThrow();
        boolean uniform = variants.stream().allMatch(v -> v.getPrice().compareTo(min) == 0);
        String formatted = NumberFormat.getIntegerInstance(Locale.KOREA).format(min.longValue()) + "원";
        return uniform ? formatted : formatted + "~";
    }
}
