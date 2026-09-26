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

    // 필수 입력 — 아이디찾기/비밀번호재설정이 이 값으로 계정을 조회하므로 비워둘 수 없다.
    // (기존에 이미 가입된, email이 비어 있는 계정은 그대로 유효 — 이건 신규 가입에만 적용.)
    @NotBlank
    @Email
    private String email;
}
