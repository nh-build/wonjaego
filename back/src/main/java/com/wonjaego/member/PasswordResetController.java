package com.wonjaego.member;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    @GetMapping("/reset-password")
    public String requestForm(Model model) {
        model.addAttribute("form", new EmailLookupForm());
        return "member/reset-password-request";
    }

    @PostMapping("/reset-password")
    public String requestSubmit(@Valid @ModelAttribute("form") EmailLookupForm form, BindingResult bindingResult,
                                 Model model) {
        if (!bindingResult.hasErrors()) {
            // 응답은 계정 존재 여부와 무관하게 항상 동일 — 계정 열거 방지.
            passwordResetService.requestReset(form.getEmail());
            model.addAttribute("submitted", true);
        }
        return "member/reset-password-request";
    }

    @GetMapping("/reset-password/confirm")
    public String confirmForm(@RequestParam String token, Model model) {
        if (!passwordResetService.isTokenUsable(token)) {
            model.addAttribute("invalidToken", true);
            return "member/reset-password-confirm";
        }
        ResetPasswordConfirmForm form = new ResetPasswordConfirmForm();
        form.setToken(token);
        model.addAttribute("form", form);
        return "member/reset-password-confirm";
    }

    @PostMapping("/reset-password/confirm")
    public String confirmSubmit(@Valid @ModelAttribute("form") ResetPasswordConfirmForm form,
                                 BindingResult bindingResult, Model model) {
        if (!bindingResult.hasErrors() && !form.getNewPassword().equals(form.getNewPasswordConfirm())) {
            bindingResult.rejectValue("newPasswordConfirm", "mismatch", "새 비밀번호가 일치하지 않습니다.");
        }
        if (!bindingResult.hasErrors()) {
            try {
                passwordResetService.resetPassword(form.getToken(), form.getNewPassword());
                return "redirect:/login?reset";
            } catch (InvalidOrExpiredTokenException e) {
                model.addAttribute("invalidToken", true);
                return "member/reset-password-confirm";
            }
        }
        return "member/reset-password-confirm";
    }
}
