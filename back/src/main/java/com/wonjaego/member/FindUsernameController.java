package com.wonjaego.member;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequiredArgsConstructor
public class FindUsernameController {

    private final MemberService memberService;

    @GetMapping("/find-username")
    public String form(Model model) {
        model.addAttribute("form", new EmailLookupForm());
        return "member/find-username";
    }

    @PostMapping("/find-username")
    public String submit(@Valid @ModelAttribute("form") EmailLookupForm form, BindingResult bindingResult,
                          Model model) {
        if (!bindingResult.hasErrors()) {
            List<String> maskedUsernames = memberService.findMaskedUsernamesByEmail(form.getEmail());
            model.addAttribute("submitted", true);
            model.addAttribute("maskedUsernames", maskedUsernames);
        }
        return "member/find-username";
    }
}
