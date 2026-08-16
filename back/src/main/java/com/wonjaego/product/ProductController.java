package com.wonjaego.product;

import com.wonjaego.member.MemberPrincipal;
import com.wonjaego.movement.MovementService;
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
public class ProductController {

    private final ProductService productService;
    private final ProductVariantService productVariantService;
    private final MovementService movementService;

    @GetMapping("/products")
    public String list(@AuthenticationPrincipal MemberPrincipal principal, Model model) {
        model.addAttribute("products", productService.listOwned(principal.getMemberId()));
        model.addAttribute("form", new ProductCreateForm());
        model.addAttribute("maxOptionGroups", ProductCreateForm.MAX_OPTION_GROUPS);
        return "products/list";
    }

    @PostMapping("/products")
    public String create(@AuthenticationPrincipal MemberPrincipal principal,
                          @Valid @ModelAttribute("form") ProductCreateForm form,
                          BindingResult bindingResult,
                          Model model) {
        if (!bindingResult.hasErrors()) {
            productService.create(principal.getMemberId(), form);
            return "redirect:/products";
        }
        model.addAttribute("products", productService.listOwned(principal.getMemberId()));
        model.addAttribute("maxOptionGroups", ProductCreateForm.MAX_OPTION_GROUPS);
        return "products/list";
    }

    @GetMapping("/products/{id}")
    public String detail(@AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long id, Model model) {
        model.addAttribute("product", productService.getOwned(principal.getMemberId(), id));
        model.addAttribute("variants", productVariantService.listForProduct(principal.getMemberId(), id));
        model.addAttribute("movements", movementService.listForProduct(principal.getMemberId(), id));
        return "products/detail";
    }

    @GetMapping("/products/{id}/edit")
    public String editForm(@AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long id, Model model) {
        Product product = productService.getOwned(principal.getMemberId(), id);
        model.addAttribute("form", ProductEditForm.from(product));
        model.addAttribute("productId", id);
        return "products/edit";
    }

    @PostMapping("/products/{id}/edit")
    public String edit(@AuthenticationPrincipal MemberPrincipal principal,
                        @PathVariable Long id,
                        @Valid @ModelAttribute("form") ProductEditForm form,
                        BindingResult bindingResult,
                        Model model) {
        // Ownership must 404 regardless of validation outcome — checked unconditionally
        // before branching on bindingResult, not just as a side effect of update() below.
        productService.getOwned(principal.getMemberId(), id);
        if (!bindingResult.hasErrors()) {
            productService.update(principal.getMemberId(), id, form);
            return "redirect:/products/" + id;
        }
        model.addAttribute("productId", id);
        return "products/edit";
    }

    @PostMapping("/products/{id}/delete")
    public String delete(@AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long id, Model model) {
        try {
            productService.delete(principal.getMemberId(), id);
            return "redirect:/products";
        } catch (ProductHasMovementsException e) {
            model.addAttribute("error", e.getMessage());
            return list(principal, model);
        }
    }
}
