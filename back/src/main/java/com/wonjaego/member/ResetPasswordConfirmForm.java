package com.wonjaego.member;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordConfirmForm {

    @NotBlank
    private String token;

    @NotBlank
    private String newPassword;

    @NotBlank
    private String newPasswordConfirm;
}
