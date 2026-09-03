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

    // 선택 입력 — 회원가입 폼에 넣을지 말지는 가입자 자유, 기존 계정은 비어 있다.
    // 프로필 수정 화면에서 변경 불가(비활성 표시)로, 변경 경로 자체가 없다.
    private String email;

    // 설정 화면의 "재고 임박 기준" — a variant with no per-variant override
    // (ProductVariant.lowStockThreshold == null) falls back to this.
    @Column(nullable = false)
    private int lowStockThreshold = DEFAULT_LOW_STOCK_THRESHOLD;

    public Member(String username, String password, String businessName, String email) {
        this.username = username;
        this.password = password;
        this.businessName = businessName;
        this.email = email;
    }

    public void updateLowStockThreshold(int lowStockThreshold) {
        this.lowStockThreshold = lowStockThreshold;
    }

    public void updateBusinessName(String businessName) {
        this.businessName = businessName;
    }

    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
    }
}
