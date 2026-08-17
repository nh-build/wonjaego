package com.wonjaego.member;

import com.wonjaego.channel.SalesChannel;
import com.wonjaego.channel.SalesChannelService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class MeController {

    private final SalesChannelService salesChannelService;

    @GetMapping("/me")
    public String me(@AuthenticationPrincipal MemberPrincipal principal, Model model) {
        model.addAttribute("member", principal.getMember());
        model.addAttribute("salesChannels", salesChannelService.listOwned(principal.getMemberId()).stream()
                .map(SalesChannel::getName)
                .toList());
        return "member/me";
    }
}
