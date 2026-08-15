package com.wonjaego.member;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SignupForm {

    @NotBlank
    private String username;

    @NotBlank
    private String password;

    @NotBlank
    private String businessName;
}
