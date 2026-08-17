package com.wonjaego.web;

import com.wonjaego.channel.SalesChannel;
import com.wonjaego.channel.SalesChannelService;
import com.wonjaego.member.MemberPrincipal;
import com.wonjaego.product.Product;
import com.wonjaego.product.ProductService;
import com.wonjaego.product.ProductVariant;
import com.wonjaego.product.ProductVariantService;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private static final int PRODUCT_PREVIEW_LIMIT = 5;

    private final ProductService productService;
    private final ProductVariantService productVariantService;
    private final SalesChannelService salesChannelService;

    @GetMapping("/splash")
    public String splash() {
        return "splash";
    }

    @GetMapping("/")
    public String home(@AuthenticationPrincipal MemberPrincipal principal, Model model) {
        if (principal == null) {
            return "home";
        }

        List<Product> products = productService.listOwned(principal.getMemberId());
        List<ProductVariant> variants = productVariantService.listOwned(principal.getMemberId());
        Map<Long, List<ProductVariant>> variantsByProductId = variants.stream()
                .collect(Collectors.groupingBy(variant -> variant.getProduct().getId()));

        List<DashboardProductRow> productRows = products.stream()
                .sorted(Comparator.comparing(Product::getId).reversed())
                .limit(PRODUCT_PREVIEW_LIMIT)
                .map(product -> toRow(product, variantsByProductId.getOrDefault(product.getId(), List.of())))
                .toList();

        long lowStockVariantCount = variants.stream().filter(ProductVariant::isLowStock).count();
        long outOfStockVariantCount = variants.stream().filter(variant -> variant.getStockQuantity() == 0).count();

        model.addAttribute("totalProductCount", products.size());
        model.addAttribute("lowStockVariantCount", lowStockVariantCount);
        model.addAttribute("outOfStockVariantCount", outOfStockVariantCount);
        model.addAttribute("productRows", productRows);
        model.addAttribute("salesChannels", salesChannelService.listOwned(principal.getMemberId()).stream()
                .map(SalesChannel::getName)
                .toList());
        return "dashboard/index";
    }

    private DashboardProductRow toRow(Product product, List<ProductVariant> productVariants) {
        int totalStock = productVariants.stream().mapToInt(ProductVariant::getStockQuantity).sum();
        boolean lowStock = productVariants.stream().anyMatch(ProductVariant::isLowStock);
        return new DashboardProductRow(product.getId(), product.getName(), product.getPhotoKey() != null, totalStock, lowStock);
    }
}
