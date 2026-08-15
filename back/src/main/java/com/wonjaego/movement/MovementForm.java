package com.wonjaego.movement;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MovementForm {

    @NotNull
    private Long productId;

    @NotNull
    private Long salesChannelId;

    @NotNull
    private MovementType type;

    @NotNull
    @Min(1)
    private Integer quantity;

    private String memo;
}
