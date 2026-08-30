package com.wonjaego.product;

import com.wonjaego.channel.ChannelType;
import com.wonjaego.common.BaseEntity;
import com.wonjaego.member.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "products",
        uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "external_channel_type", "external_product_id"}))
public class Product extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false)
    private String name;

    // Opaque key issued by FileStorage — never a filesystem path, so this stays valid
    // regardless of which FileStorage implementation is in use.
    private String photoKey;

    // A channel-hosted image URL (e.g. Zigzag's own CDN) — distinct from photoKey, which is
    // only for photos wonjaego itself stores via FileStorage (ADR 0003). Null unless imported.
    private String externalImageUrl;

    // Both null for a manually-registered product (ADR 0006). Both set for a product
    // imported via 채널 연동 — externalProductId is that channel's own product id, used to
    // dedupe re-imports (find-or-create instead of always inserting).
    @Enumerated(EnumType.STRING)
    private ChannelType externalChannelType;

    private String externalProductId;

    // Product-level cost basis, distinct from ProductVariant.price (the per-combo selling
    // price, ADR 0008). Optional — used only to show margin/margin rate against the base price.
    private BigDecimal costPrice;

    public Product(Member member, String name) {
        this.member = member;
        this.name = name;
    }

    public Product(Member member, String name, ChannelType externalChannelType, String externalProductId) {
        this.member = member;
        this.name = name;
        this.externalChannelType = externalChannelType;
        this.externalProductId = externalProductId;
    }

    public void updateInfo(String name) {
        this.name = name;
    }

    public void updatePhotoKey(String photoKey) {
        this.photoKey = photoKey;
    }

    public void updateExternalImageUrl(String externalImageUrl) {
        this.externalImageUrl = externalImageUrl;
    }

    public void updateCostPrice(BigDecimal costPrice) {
        this.costPrice = costPrice;
    }

    // Single source of truth for "what image URL represents this product" — a locally
    // stored photo takes priority (served via FileStorage behind /products/{id}/photo,
    // ADR 0003), falling back to a channel-hosted image URL for an imported product with
    // no local photo. Every screen that shows a product thumbnail (list, 입고/출고 search
    // and selection) resolves it this same way rather than re-deriving the rule.
    public String resolveImageUrl() {
        return photoKey != null ? "/products/" + getId() + "/photo" : externalImageUrl;
    }
}
