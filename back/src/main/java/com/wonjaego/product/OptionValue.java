package com.wonjaego.product;

import com.wonjaego.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "option_values")
public class OptionValue extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "option_group_id", nullable = false)
    private OptionGroup optionGroup;

    // "value" is a reserved SQL keyword in H2 — column renamed to avoid a DDL syntax error.
    @Column(name = "value_text", nullable = false)
    private String value;

    public OptionValue(OptionGroup optionGroup, String value) {
        this.optionGroup = optionGroup;
        this.value = value;
    }
}
