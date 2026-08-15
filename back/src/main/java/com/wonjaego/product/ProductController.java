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

@Controller
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping("/products")
    public String list(@AuthenticationPrincipal MemberPrincipal principal, Model model) {
        model.addAttribute("products", productService.listOwned(principal.getMemberId()));
        model.addAttribute("form", new ProductCreateForm());
        return "products/list";
    }

    @PostMapping("/products")
    public String create(@AuthenticationPrincipal MemberPrincipal principal,
                          @Valid @ModelAttribute("form") ProductCreateForm form,
                          BindingResult bindingResult,
                          Model model) {
        if (!bindingResult.hasErrors()) {
            try {
                productService.create(principal.getMemberId(), form);
                return "redirect:/products";
            } catch (DuplicateSkuException e) {
                bindingResult.rejectValue("sku", "duplicate", e.getMessage());
            }
        }
        model.addAttribute("products", productService.listOwned(principal.getMemberId()));
        return "products/list";
    }

    @GetMapping("/products/{id}")
    public String detail(@AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long id, Model model) {
        model.addAttribute("product", productService.getOwned(principal.getMemberId(), id));
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
            try {
                productService.update(principal.getMemberId(), id, form);
                return "redirect:/products/" + id;
            } catch (DuplicateSkuException e) {
                bindingResult.rejectValue("sku", "duplicate", e.getMessage());
            }
        }
        model.addAttribute("productId", id);
        return "products/edit";
    }

    @PostMapping("/products/{id}/delete")
    public String delete(@AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long id) {
        productService.delete(principal.getMemberId(), id);
        return "redirect:/products";
    }
}
