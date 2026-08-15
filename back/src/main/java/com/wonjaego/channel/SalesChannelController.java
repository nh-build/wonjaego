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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequiredArgsConstructor
public class SalesChannelController {

    private final SalesChannelService salesChannelService;

    @GetMapping("/channels")
    public String list(@AuthenticationPrincipal MemberPrincipal principal, Model model) {
        model.addAttribute("channels", salesChannelService.listOwned(principal.getMemberId()));
        model.addAttribute("form", new SalesChannelForm());
        return "channels/list";
    }

    @PostMapping("/channels")
    public String create(@AuthenticationPrincipal MemberPrincipal principal,
                          @Valid @ModelAttribute("form") SalesChannelForm form,
                          BindingResult bindingResult,
                          Model model) {
        if (!bindingResult.hasErrors()) {
            try {
                salesChannelService.create(principal.getMemberId(), form);
                return "redirect:/channels";
            } catch (DuplicateChannelNameException e) {
                bindingResult.rejectValue("name", "duplicate", e.getMessage());
            }
        }
        model.addAttribute("channels", salesChannelService.listOwned(principal.getMemberId()));
        return "channels/list";
    }

    @GetMapping("/channels/{id}/edit")
    public String editForm(@AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long id, Model model) {
        SalesChannel channel = salesChannelService.getOwned(principal.getMemberId(), id);
        model.addAttribute("form", SalesChannelForm.from(channel));
        model.addAttribute("channelId", id);
        return "channels/edit";
    }

    @PostMapping("/channels/{id}/edit")
    public String edit(@AuthenticationPrincipal MemberPrincipal principal,
                        @PathVariable Long id,
                        @Valid @ModelAttribute("form") SalesChannelForm form,
                        BindingResult bindingResult,
                        Model model) {
        // Ownership must 404 regardless of validation outcome — checked unconditionally
        // before branching on bindingResult, not just as a side effect of update() below.
        salesChannelService.getOwned(principal.getMemberId(), id);
        if (!bindingResult.hasErrors()) {
            try {
                salesChannelService.update(principal.getMemberId(), id, form);
                return "redirect:/channels";
            } catch (DuplicateChannelNameException e) {
                bindingResult.rejectValue("name", "duplicate", e.getMessage());
            }
        }
        model.addAttribute("channelId", id);
        return "channels/edit";
    }

    @PostMapping("/channels/{id}/delete")
    public String delete(@AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long id, Model model) {
        try {
            salesChannelService.delete(principal.getMemberId(), id);
            return "redirect:/channels";
        } catch (SalesChannelHasMovementsException e) {
            model.addAttribute("error", e.getMessage());
            return list(principal, model);
        }
    }
}
