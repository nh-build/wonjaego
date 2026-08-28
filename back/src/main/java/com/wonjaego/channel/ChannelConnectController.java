package com.wonjaego.channel;

import com.wonjaego.member.MemberPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequiredArgsConstructor
public class ChannelConnectController {

    private final ChannelCredentialService channelCredentialService;
    private final ChannelCredentialRepository channelCredentialRepository;

    @GetMapping("/channels/connect")
    public String form(@AuthenticationPrincipal MemberPrincipal principal, Model model) {
        model.addAttribute("form", new ChannelConnectForm());
        addFormOptions(principal, model);
        return "channels/connect";
    }

    @PostMapping("/channels/connect")
    public String connect(@AuthenticationPrincipal MemberPrincipal principal,
                           @Valid @ModelAttribute("form") ChannelConnectForm form,
                           BindingResult bindingResult,
                           Model model) {
        if (!bindingResult.hasErrors()) {
            if (form.getChannelType() != ChannelType.ZIGZAG) {
                bindingResult.reject("invalid", "아직 연동을 지원하지 않는 채널이에요.");
            } else if (isBlank(form.getAccessKey()) || isBlank(form.getSecretKey())) {
                bindingResult.reject("invalid", "Access Key와 Secret Key를 모두 입력해주세요.");
            } else {
                // 연동만 한다 — 상품 가져오기는 관리 화면에서 역할을 상품 소스로 정한 뒤
                // 별도로 트리거하는 동작이다 (연동 먼저 → 관리화면에서 역할 선택).
                ChannelCredential credential = channelCredentialService.connect(principal.getMemberId(), ChannelType.ZIGZAG,
                        form.getAccessKey().trim(), form.getSecretKey().trim());
                return "redirect:/channels/" + credential.getId();
            }
        }
        model.addAttribute("form", form);
        addFormOptions(principal, model);
        return "channels/connect";
    }

    private void addFormOptions(MemberPrincipal principal, Model model) {
        model.addAttribute("channelTypes", ChannelType.values());
        model.addAttribute("zigzagConnected",
                channelCredentialRepository.findByMemberIdAndChannelType(principal.getMemberId(), ChannelType.ZIGZAG).isPresent());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
