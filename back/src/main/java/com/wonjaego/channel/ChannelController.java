package com.wonjaego.channel;

import com.wonjaego.integration.zigzag.ZigzagApiException;
import com.wonjaego.integration.zigzag.ZigzagImportResult;
import com.wonjaego.member.MemberPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// "연동 먼저 → 관리화면에서 역할 선택" — /channels (연동 목록, no role UI) and /channels/{id}
// (연동 상태 + 역할 선택 + 상품 불러오기). Distinct from /channels/tags (SalesChannel, a
// seller's free-text channel tag, ADR 0005).
@Controller
@RequiredArgsConstructor
public class ChannelController {

    private final ChannelService channelService;

    @GetMapping("/channels")
    public String list(@AuthenticationPrincipal MemberPrincipal principal, Model model) {
        model.addAttribute("items", channelService.listChannels(principal.getMemberId()));
        return "channels/list";
    }

    @GetMapping("/channels/{id}")
    public String manage(@AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long id, Model model) {
        model.addAttribute("channel", channelService.getManageView(principal.getMemberId(), id));
        return "channels/manage";
    }

    @PostMapping("/channels/{id}/role")
    public String changeRole(@AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long id,
                              @RequestParam ChannelRole role, RedirectAttributes redirectAttributes) {
        try {
            channelService.changeRole(principal.getMemberId(), id, role);
        } catch (InvalidChannelOperationException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/channels/" + id;
    }

    @PostMapping("/channels/{id}/disconnect")
    public String disconnect(@AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long id) {
        channelService.disconnect(principal.getMemberId(), id);
        return "redirect:/channels";
    }

    @PostMapping("/channels/{id}/import")
    public String importProducts(@AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long id,
                                  RedirectAttributes redirectAttributes) {
        try {
            ZigzagImportResult result = channelService.importProducts(principal.getMemberId(), id);
            redirectAttributes.addFlashAttribute("importResult", result);
        } catch (ZigzagApiException | InvalidChannelOperationException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/channels/" + id;
    }
}
