package com.wonjaego.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.wonjaego.member.Member;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

// ADR 0008 — the "대표가" shown on the product list/detail screens must render as
// "19,000원~" (comma-grouped, "원" suffix, "~" only when variants' prices differ).
class ProductVariantPriceRangeTest {

    private final Member member = new Member("seller", "password123", "가게", null);
    private final Product product = new Product(member, "상품");

    @Test
    void 모든_변형_가격이_같으면_물결_없이_한_값으로_표시된다() {
        List<ProductVariant> variants = List.of(
                new ProductVariant(product, Set.of(), new BigDecimal("19000")),
                new ProductVariant(product, Set.of(), new BigDecimal("19000")));

        assertThat(ProductVariant.formatPriceRange(variants)).isEqualTo("19,000원");
    }

    @Test
    void 변형_가격이_다르면_최저가에_물결이_붙는다() {
        List<ProductVariant> variants = List.of(
                new ProductVariant(product, Set.of(), new BigDecimal("19000")),
                new ProductVariant(product, Set.of(), new BigDecimal("25000")));

        assertThat(ProductVariant.formatPriceRange(variants)).isEqualTo("19,000원~");
    }

    @Test
    void 변형이_없으면_대시로_표시된다() {
        assertThat(ProductVariant.formatPriceRange(List.of())).isEqualTo("-");
    }
}
