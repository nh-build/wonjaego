package com.wonjaego.web;

import com.wonjaego.member.MemberPrincipal;
import com.wonjaego.product.ProductService;
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

    private final ProductService productService;
    private final ProductVariantService productVariantService;

    @GetMapping("/splash")
    public String splash() {
        return "splash";
    }

    @GetMapping("/")
    public String home(@AuthenticationPrincipal MemberPrincipal principal, Model model) {
        if (principal == null) {
            return "home";
        }

        List<ProductVariant> variants = productVariantService.listOwned(principal.getMemberId());
        long lowStockVariantCount = variants.stream().filter(ProductVariant::isLowStock).count();
        long outOfStockVariantCount = variants.stream().filter(variant -> variant.getStockQuantity() == 0).count();

        model.addAttribute("totalProductCount", productService.listOwned(principal.getMemberId()).size());
        model.addAttribute("lowStockVariantCount", lowStockVariantCount);
        model.addAttribute("outOfStockVariantCount", outOfStockVariantCount);
        return "dashboard/index";
    }
}
