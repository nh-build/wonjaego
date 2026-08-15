package com.wonjaego.movement;

import com.wonjaego.channel.SalesChannel;
import com.wonjaego.common.BaseEntity;
import com.wonjaego.product.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "movements")
public class Movement extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_channel_id", nullable = false)
    private SalesChannel salesChannel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MovementType type;

    @Column(nullable = false)
    private int quantityChange;

    private String memo;

    public Movement(Product product, SalesChannel salesChannel, MovementType type, int quantityChange, String memo) {
        this.product = product;
        this.salesChannel = salesChannel;
        this.type = type;
        this.quantityChange = quantityChange;
        this.memo = memo;
    }
}
