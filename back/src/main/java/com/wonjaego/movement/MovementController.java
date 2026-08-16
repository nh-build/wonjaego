package com.wonjaego.movement;

import com.wonjaego.channel.SalesChannelService;
import com.wonjaego.member.MemberPrincipal;
import com.wonjaego.product.InsufficientStockException;
import com.wonjaego.product.ProductVariant;
import com.wonjaego.product.ProductVariantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class MovementController {

    private final MovementService movementService;
    private final ProductVariantService productVariantService;
    private final SalesChannelService salesChannelService;

    @GetMapping("/movements/new")
    public String newForm(@AuthenticationPrincipal MemberPrincipal principal,
                           @RequestParam(required = false) Long variantId,
                           Model model) {
        MovementForm form = new MovementForm();
        form.setVariantId(variantId);
        model.addAttribute("form", form);
        addFormOptions(principal, model);
        return "movements/new";
    }

    @PostMapping("/movements/new")
    public String create(@AuthenticationPrincipal MemberPrincipal principal,
                          @Valid @ModelAttribute("form") MovementForm form,
                          BindingResult bindingResult,
                          Model model) {
        // Ownership must 404 whenever an id is present, regardless of validation outcome
        // on the other fields — not just as a side effect of record() in the happy path.
        ProductVariant variant = null;
        if (form.getVariantId() != null) {
            variant = productVariantService.getOwned(principal.getMemberId(), form.getVariantId());
        }
        if (form.getSalesChannelId() != null) {
            salesChannelService.getOwned(principal.getMemberId(), form.getSalesChannelId());
        }
        if (form.getNewVariantId() != null) {
            productVariantService.getOwned(principal.getMemberId(), form.getNewVariantId());
        }
        if (!bindingResult.hasErrors()) {
            try {
                if (form.getType() == MovementType.EXCHANGE) {
                    movementService.recordExchange(principal.getMemberId(), form.getVariantId(), form.getSalesChannelId(),
                            form.getNewVariantId(), form.getQuantity(), form.getMemo());
                } else {
                    movementService.record(principal.getMemberId(), form.getVariantId(), form.getSalesChannelId(),
                            form.getType(), form.getQuantity(), form.getMemo());
                }
                return "redirect:/products/" + variant.getProduct().getId();
            } catch (InsufficientStockException e) {
                bindingResult.reject("insufficientStock", e.getMessage());
            }
        }
        addFormOptions(principal, model);
        return "movements/new";
    }

    private void addFormOptions(MemberPrincipal principal, Model model) {
        model.addAttribute("variants", productVariantService.listOwned(principal.getMemberId()));
        model.addAttribute("channels", salesChannelService.listOwned(principal.getMemberId()));
        model.addAttribute("types", MovementType.values());
    }
}
