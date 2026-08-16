package com.wonjaego.web;

import com.wonjaego.member.MemberPrincipal;
import com.wonjaego.product.Product;
import com.wonjaego.product.ProductService;
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

    @GetMapping("/")
    public String home(@AuthenticationPrincipal MemberPrincipal principal, Model model) {
        if (principal == null) {
            return "home";
        }

        List<Product> products = productService.listOwned(principal.getMemberId());
        int totalStockQuantity = products.stream().mapToInt(Product::getStockQuantity).sum();
        List<Product> lowStockProducts = products.stream().filter(Product::isLowStock).toList();

        model.addAttribute("totalProductCount", products.size());
        model.addAttribute("totalStockQuantity", totalStockQuantity);
        model.addAttribute("lowStockProducts", lowStockProducts);
        return "dashboard/index";
    }
}
