package com.wonjaego.movement;

import com.wonjaego.channel.SalesChannelService;
import com.wonjaego.member.MemberPrincipal;
import com.wonjaego.product.InsufficientStockException;
import com.wonjaego.product.ProductService;
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
    private final ProductService productService;
    private final SalesChannelService salesChannelService;

    @GetMapping("/movements/new")
    public String newForm(@AuthenticationPrincipal MemberPrincipal principal,
                           @RequestParam(required = false) Long productId,
                           Model model) {
        MovementForm form = new MovementForm();
        form.setProductId(productId);
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
        if (form.getProductId() != null) {
            productService.getOwned(principal.getMemberId(), form.getProductId());
        }
        if (form.getSalesChannelId() != null) {
            salesChannelService.getOwned(principal.getMemberId(), form.getSalesChannelId());
        }
        if (!bindingResult.hasErrors()) {
            try {
                movementService.record(principal.getMemberId(), form.getProductId(), form.getSalesChannelId(),
                        form.getType(), form.getQuantity(), form.getMemo());
                return "redirect:/products/" + form.getProductId();
            } catch (InsufficientStockException e) {
                bindingResult.reject("insufficientStock", e.getMessage());
            }
        }
        addFormOptions(principal, model);
        return "movements/new";
    }

    private void addFormOptions(MemberPrincipal principal, Model model) {
        model.addAttribute("products", productService.listOwned(principal.getMemberId()));
        model.addAttribute("channels", salesChannelService.listOwned(principal.getMemberId()));
        model.addAttribute("types", MovementType.values());
    }
}
