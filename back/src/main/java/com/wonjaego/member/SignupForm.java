package com.wonjaego.member;

import jakarta.validation.constraints.Email;
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

    // 선택 입력 — 비워도 가입 가능.
    @Email
    private String email;
}
