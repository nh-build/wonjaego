package com.wonjaego.web;

import com.wonjaego.member.MemberPrincipal;
import com.wonjaego.member.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class MoreController {

    private final MemberService memberService;

    // Fresh fetch, not principal.getMember() — the session-cached principal wouldn't
    // reflect a businessName change from /me/edit until the seller logs back in.
    @GetMapping("/more")
    public String more(@AuthenticationPrincipal MemberPrincipal principal, Model model) {
        model.addAttribute("member", memberService.getMember(principal.getMemberId()));
        return "more/index";
    }
}
