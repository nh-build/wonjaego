package com.wonjaego.web;

import com.wonjaego.member.MemberPrincipal;
import com.wonjaego.member.MemberService;
import com.wonjaego.product.ProductListItem;
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

    private static final int LOW_STOCK_PREVIEW_LIMIT = 5;

    private final ProductService productService;
    private final ProductVariantService productVariantService;
    private final MemberService memberService;

    @GetMapping("/splash")
    public String splash() {
        return "splash";
    }

    @GetMapping("/")
    public String home(@AuthenticationPrincipal MemberPrincipal principal, Model model) {
        if (principal == null) {
            return "home";
        }

        int lowStockThreshold = memberService.getLowStockThreshold(principal.getMemberId());
        List<ProductVariant> variants = productVariantService.listOwned(principal.getMemberId());
        long lowStockVariantCount = variants.stream().filter(v -> v.isLowStock(lowStockThreshold)).count();
        long outOfStockVariantCount = variants.stream().filter(variant -> variant.getStockQuantity() == 0).count();

        model.addAttribute("totalProductCount", productService.listOwned(principal.getMemberId()).size());
        model.addAttribute("lowStockVariantCount", lowStockVariantCount);
        model.addAttribute("outOfStockVariantCount", outOfStockVariantCount);

        // "재고 확인이 필요해요" — same 재고부족 definition/ordering as /products?stock=low
        // (재고 적은순 default sort), just capped to a short home-screen preview.
        List<ProductListItem> lowStockItems = productService.listPage(principal.getMemberId(), null, "low", null, 0)
                .items().stream().limit(LOW_STOCK_PREVIEW_LIMIT).toList();
        model.addAttribute("lowStockItems", lowStockItems);
        return "dashboard/index";
    }
}
