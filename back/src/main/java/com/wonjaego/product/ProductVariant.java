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
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "product_variants", uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "sku"}))
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

    public ProductVariant(Product product, Set<OptionValue> optionValues) {
        this.member = product.getMember();
        this.product = product;
        this.optionValues = optionValues;
        this.stockQuantity = 0;
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
}
