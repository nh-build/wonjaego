package com.wonjaego.member;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProfileEditForm {

    // 프로필 수정 화면의 "이름" — 이 앱에서 회원의 표시 이름은 줄곧 상호명(businessName)이었고,
    // 별도의 개인 이름 필드는 없다(회원가입도 상호명만 받는다).
    @NotBlank
    private String businessName;
}
