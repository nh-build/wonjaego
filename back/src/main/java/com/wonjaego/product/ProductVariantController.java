package com.wonjaego.product;

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
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequiredArgsConstructor
@RequestMapping("/products/{productId}/variants/{variantId}")
public class ProductVariantController {

    private final ProductVariantService productVariantService;

    @GetMapping("/edit")
    public String editForm(@AuthenticationPrincipal MemberPrincipal principal,
                            @PathVariable Long productId, @PathVariable Long variantId,
                            Model model) {
        ProductVariant variant = getOwnedVariant(principal.getMemberId(), productId, variantId);
        model.addAttribute("form", ProductVariantEditForm.from(variant));
        model.addAttribute("variant", variant);
        model.addAttribute("productId", productId);
        return "products/variant-edit";
    }

    @PostMapping("/edit")
    public String edit(@AuthenticationPrincipal MemberPrincipal principal,
                        @PathVariable Long productId, @PathVariable Long variantId,
                        @Valid @ModelAttribute("form") ProductVariantEditForm form,
                        BindingResult bindingResult,
                        Model model) {
        // Ownership must 404 regardless of validation outcome — checked unconditionally
        // before branching on bindingResult, not just as a side effect of update() below.
        ProductVariant variant = getOwnedVariant(principal.getMemberId(), productId, variantId);
        if (!bindingResult.hasErrors()) {
            try {
                productVariantService.update(principal.getMemberId(), variantId, form);
                return "redirect:/products/" + productId;
            } catch (DuplicateSkuException e) {
                bindingResult.rejectValue("sku", "duplicate", e.getMessage());
            }
        }
        model.addAttribute("variant", variant);
        model.addAttribute("productId", productId);
        return "products/variant-edit";
    }

    // A variant edited from a product's own page must actually belong to that product —
    // not just to the logged-in member (who may own several products' worth of variants).
    private ProductVariant getOwnedVariant(Long memberId, Long productId, Long variantId) {
        ProductVariant variant = productVariantService.getOwned(memberId, variantId);
        if (!variant.getProduct().getId().equals(productId)) {
            throw new ProductVariantNotFoundException(variantId);
        }
        return variant;
    }
}
