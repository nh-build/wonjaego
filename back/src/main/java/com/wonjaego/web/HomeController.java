package com.wonjaego.web;

import com.wonjaego.member.MemberPrincipal;
import com.wonjaego.product.ProductVariant;
import com.wonjaego.product.ProductVariantService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final ProductVariantService productVariantService;

    @GetMapping("/")
    public String home(@AuthenticationPrincipal MemberPrincipal principal, Model model) {
        if (principal == null) {
            return "home";
        }

        List<ProductVariant> variants = productVariantService.listOwned(principal.getMemberId());
        int totalStockQuantity = variants.stream().mapToInt(ProductVariant::getStockQuantity).sum();
        List<ProductVariant> lowStockVariants = variants.stream().filter(ProductVariant::isLowStock).toList();

        model.addAttribute("totalVariantCount", variants.size());
        model.addAttribute("totalStockQuantity", totalStockQuantity);
        model.addAttribute("lowStockVariants", lowStockVariants);
        return "dashboard/index";
    }
}
