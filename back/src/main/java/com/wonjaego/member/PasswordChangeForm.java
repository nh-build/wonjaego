package com.wonjaego.member;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordChangeForm {

    @NotBlank
    private String currentPassword;

    @NotBlank
    private String newPassword;

    // 확인 입력은 서버로 전송되지만 DB에는 저장되지 않는다 — 컨트롤러가 newPassword와
    // 일치하는지만 확인하고 버린다(회원가입 폼의 클라이언트 측 확인과 같은 역할을
    // 서버에서도 한 번 더).
    @NotBlank
    private String newPasswordConfirm;
}
