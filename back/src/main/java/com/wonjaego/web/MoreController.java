package com.wonjaego.web;

import com.wonjaego.member.MemberPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class MoreController {

    @GetMapping("/more")
    public String more(@AuthenticationPrincipal MemberPrincipal principal, Model model) {
        model.addAttribute("member", principal.getMember());
        return "more/index";
    }
}
