package com.wonjaego.product;

import com.wonjaego.common.BaseEntity;
import com.wonjaego.member.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "products")
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

    public Product(Member member, String name, BigDecimal price) {
        this.member = member;
        this.name = name;
        this.price = price;
    }

    public void updateInfo(String name, BigDecimal price) {
        this.name = name;
        this.price = price;
    }

    public void updatePhotoKey(String photoKey) {
        this.photoKey = photoKey;
    }
}
