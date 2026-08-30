package com.wonjaego.member;

import com.wonjaego.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "members")
public class Member extends BaseEntity {

    // Canonical source for "재고 임박" defaults — ProductVariant.DEFAULT_LOW_STOCK_THRESHOLD
    // mirrors this rather than the other way around, since the per-member setting (below) is
    // the thing a variant's own override falls back to.
    public static final int DEFAULT_LOW_STOCK_THRESHOLD = 5;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String businessName;

    // 설정 화면의 "재고 임박 기준" — a variant with no per-variant override
    // (ProductVariant.lowStockThreshold == null) falls back to this.
    @Column(nullable = false)
    private int lowStockThreshold = DEFAULT_LOW_STOCK_THRESHOLD;

    public Member(String username, String password, String businessName) {
        this.username = username;
        this.password = password;
        this.businessName = businessName;
    }

    public void updateLowStockThreshold(int lowStockThreshold) {
        this.lowStockThreshold = lowStockThreshold;
    }
}
