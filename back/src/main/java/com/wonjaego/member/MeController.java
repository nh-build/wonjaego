package com.wonjaego.member;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MeController {

    @GetMapping("/me")
    public String me(@AuthenticationPrincipal MemberPrincipal principal, Model model) {
        model.addAttribute("member", principal.getMember());
        return "member/me";
    }
}
