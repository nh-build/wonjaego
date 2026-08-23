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

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    // Opaque key issued by FileStorage — never a filesystem path, so this stays valid
    // regardless of which FileStorage implementation is in use.
    private String photoKey;

    // Both null for a manually-registered product (ADR 0006). Both set for a product
    // imported via 채널 연동 — externalProductId is that channel's own product id, used to
    // dedupe re-imports (find-or-create instead of always inserting).
    @Enumerated(EnumType.STRING)
    private ChannelType externalChannelType;

    private String externalProductId;

    public Product(Member member, String name, BigDecimal price) {
        this.member = member;
        this.name = name;
        this.price = price;
    }

    public Product(Member member, String name, BigDecimal price, ChannelType externalChannelType, String externalProductId) {
        this.member = member;
        this.name = name;
        this.price = price;
        this.externalChannelType = externalChannelType;
        this.externalProductId = externalProductId;
    }

    public void updateInfo(String name, BigDecimal price) {
        this.name = name;
        this.price = price;
    }

    public void updatePhotoKey(String photoKey) {
        this.photoKey = photoKey;
    }
}
