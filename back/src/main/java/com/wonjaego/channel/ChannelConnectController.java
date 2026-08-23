package com.wonjaego.channel;

import com.wonjaego.integration.zigzag.ZigzagApiException;
import com.wonjaego.integration.zigzag.ZigzagImportResult;
import com.wonjaego.integration.zigzag.ZigzagProductImportService;
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
    private final ZigzagProductImportService zigzagProductImportService;

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
                try {
                    channelCredentialService.saveOrUpdate(principal.getMemberId(), ChannelType.ZIGZAG,
                            form.getAccessKey().trim(), form.getSecretKey().trim());
                    ZigzagImportResult result = zigzagProductImportService.importProducts(principal.getMemberId());
                    model.addAttribute("importResult", result);
                } catch (ZigzagApiException e) {
                    // The exception message already carries the raw API/HTTP error — show it as-is.
                    bindingResult.reject("invalid", e.getMessage());
                }
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
