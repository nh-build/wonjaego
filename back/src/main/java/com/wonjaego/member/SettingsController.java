package com.wonjaego.member;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequiredArgsConstructor
public class SettingsController {

    private final MemberService memberService;

    @GetMapping("/settings")
    public String settings(@AuthenticationPrincipal MemberPrincipal principal, Model model) {
        model.addAttribute("lowStockThreshold", memberService.getLowStockThreshold(principal.getMemberId()));
        return "settings/index";
    }

    // Instant-save on each stepper click (mockup shows no separate save button) — the value
    // takes effect immediately for the home tile / list filter / badge colors that all read
    // Member.lowStockThreshold live on their own next request.
    @PostMapping("/settings/low-stock-threshold")
    @ResponseBody
    public ResponseEntity<LowStockThresholdRequest> updateLowStockThreshold(
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestBody LowStockThresholdRequest request) {
        try {
            memberService.updateLowStockThreshold(principal.getMemberId(), request.value());
        } catch (InvalidLowStockThresholdException e) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(request);
    }
}
