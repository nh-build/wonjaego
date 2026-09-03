package com.wonjaego.member;

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
public class MeController {

    private final MemberService memberService;

    // Always re-fetches (never principal.getMember()) — the session-cached principal
    // wouldn't reflect a businessName change from /me/edit until the seller logs back in.
    @GetMapping("/me")
    public String me(@AuthenticationPrincipal MemberPrincipal principal, Model model) {
        model.addAttribute("member", memberService.getMember(principal.getMemberId()));
        return "member/me";
    }

    @GetMapping("/me/edit")
    public String editForm(@AuthenticationPrincipal MemberPrincipal principal, Model model) {
        Member member = memberService.getMember(principal.getMemberId());
        ProfileEditForm form = new ProfileEditForm();
        form.setBusinessName(member.getBusinessName());
        model.addAttribute("form", form);
        model.addAttribute("email", member.getEmail());
        return "member/profile-edit";
    }

    @PostMapping("/me/edit")
    public String edit(@AuthenticationPrincipal MemberPrincipal principal,
                        @Valid @ModelAttribute("form") ProfileEditForm form,
                        BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("email", memberService.getMember(principal.getMemberId()).getEmail());
            return "member/profile-edit";
        }
        memberService.updateBusinessName(principal.getMemberId(), form.getBusinessName());
        return "redirect:/me";
    }

    @GetMapping("/me/password")
    public String passwordForm(Model model) {
        model.addAttribute("form", new PasswordChangeForm());
        return "member/password-change";
    }

    @PostMapping("/me/password")
    public String changePassword(@AuthenticationPrincipal MemberPrincipal principal,
                                  @Valid @ModelAttribute("form") PasswordChangeForm form,
                                  BindingResult bindingResult, Model model) {
        if (!bindingResult.hasErrors() && !form.getNewPassword().equals(form.getNewPasswordConfirm())) {
            bindingResult.rejectValue("newPasswordConfirm", "mismatch", "새 비밀번호가 일치하지 않습니다.");
        }
        if (!bindingResult.hasErrors()) {
            try {
                memberService.changePassword(principal.getMemberId(), form.getCurrentPassword(), form.getNewPassword());
                return "redirect:/me";
            } catch (InvalidCurrentPasswordException e) {
                bindingResult.rejectValue("currentPassword", "invalid", e.getMessage());
            }
        }
        return "member/password-change";
    }
}
