package com.wonjaego.member;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

// 아이디 찾기 / 비밀번호 재설정 요청 폼 — 둘 다 이메일 하나만 받는다.
@Getter
@Setter
public class EmailLookupForm {

    @NotBlank
    @Email
    private String email;
}
