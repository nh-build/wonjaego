package com.wonjaego.product;

import com.wonjaego.ai.NameSuggestionClient;
import com.wonjaego.member.MemberPrincipal;
import com.wonjaego.movement.MovementService;
import com.wonjaego.storage.FileStorage;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductVariantService productVariantService;
    private final MovementService movementService;
    private final FileStorage fileStorage;
    private final NameSuggestionClient nameSuggestionClient;

    @GetMapping("/products")
    public String list(@AuthenticationPrincipal MemberPrincipal principal,
                        @RequestParam(required = false) String stock,
                        Model model) {
        model.addAttribute("products", filteredProducts(principal.getMemberId(), stock));
        model.addAttribute("stockFilter", stock);
        model.addAttribute("form", new ProductCreateForm());
        return "products/list";
    }

    // Backs the dashboard's "재고 부족"/"품절" tiles — "stock" is only ever "low" or "out"
    // from those links; any other value (missing, typo, tampered) falls back to the
    // unfiltered list rather than silently showing an empty/wrong subset.
    private List<Product> filteredProducts(Long memberId, String stock) {
        List<Product> products = productService.listOwned(memberId);
        if (!"low".equals(stock) && !"out".equals(stock)) {
            return products;
        }
        Map<Long, List<ProductVariant>> variantsByProductId = productVariantService.listOwned(memberId).stream()
                .collect(Collectors.groupingBy(variant -> variant.getProduct().getId()));
        return products.stream()
                .filter(product -> variantsByProductId.getOrDefault(product.getId(), List.of()).stream()
                        .anyMatch("out".equals(stock) ? v -> v.getStockQuantity() == 0 : ProductVariant::isLowStock))
                .toList();
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
            } catch (InvalidPhotoException e) {
                bindingResult.rejectValue("photo", "invalid", e.getMessage());
            } catch (InvalidStockDataException e) {
                bindingResult.reject("invalid", e.getMessage());
            }
        }
        model.addAttribute("products", productService.listOwned(principal.getMemberId()));
        return "products/list";
    }

    @GetMapping("/products/{id}")
    public String detail(@AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long id, Model model) {
        model.addAttribute("product", productService.getOwned(principal.getMemberId(), id));
        model.addAttribute("variants", productVariantService.listForProduct(principal.getMemberId(), id));
        model.addAttribute("movements", movementService.listForProduct(principal.getMemberId(), id));
        return "products/detail";
    }

    @GetMapping("/products/{id}/photo")
    public ResponseEntity<Resource> photo(@AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long id) {
        Product product = productService.getOwned(principal.getMemberId(), id);
        String photoKey = product.getPhotoKey();
        if (photoKey == null) {
            throw new ProductPhotoNotFoundException(id);
        }
        MediaType mediaType = MediaTypeFactory.getMediaType(photoKey).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().contentType(mediaType).body(fileStorage.load(photoKey));
    }

    // No ownership check needed here (unlike every other endpoint in this controller) —
    // this runs before any Product exists. Login alone is already enforced by SecurityConfig's
    // anyRequest().authenticated(), so no @AuthenticationPrincipal parameter is needed.
    @PostMapping("/products/name-suggestions")
    @ResponseBody
    public List<String> suggestNames(@RequestBody NameSuggestionRequest request) {
        List<String> keywords = parseKeywords(request.keywords());
        if (keywords.isEmpty()) {
            throw new InvalidNameSuggestionRequestException("포인트 단어를 입력해주세요.");
        }
        return nameSuggestionClient.suggest(keywords, request.mood());
    }

    // Backs the 입고하기/출고하기 screens' product-name search dropdown.
    @GetMapping("/products/search")
    @ResponseBody
    public List<ProductSearchResult> search(@AuthenticationPrincipal MemberPrincipal principal,
                                             @RequestParam(required = false) String q) {
        String query = q == null ? "" : q.trim();
        if (query.isEmpty()) {
            return List.of();
        }
        return productService.search(principal.getMemberId(), query).stream()
                .map(product -> new ProductSearchResult(product.getId(), product.getName()))
                .toList();
    }

    // Backs the 입고하기/출고하기 screens' combo table once a product is selected by name.
    @GetMapping("/products/{id}/stock-entry")
    @ResponseBody
    public ProductStockEntryResponse stockEntry(@AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long id) {
        Product product = productService.getOwned(principal.getMemberId(), id);
        List<ProductVariant> variants = productVariantService.listForProduct(principal.getMemberId(), id);
        return toStockEntryResponse(product, variants, null);
    }

    // Backs the 입고하기/출고하기 screens' barcode scan/manual-entry flow — SKU doubles as
    // the barcode value (no separate barcode field on ProductVariant).
    @GetMapping("/products/by-sku")
    @ResponseBody
    public ProductStockEntryResponse bySku(@AuthenticationPrincipal MemberPrincipal principal, @RequestParam String sku) {
        ProductVariant matched = productVariantService.getOwnedBySku(principal.getMemberId(), sku);
        Long productId = matched.getProduct().getId();
        Product product = productService.getOwned(principal.getMemberId(), productId);
        List<ProductVariant> variants = productVariantService.listForProduct(principal.getMemberId(), productId);
        return toStockEntryResponse(product, variants, matched.getSku());
    }

    private ProductStockEntryResponse toStockEntryResponse(Product product, List<ProductVariant> variants, String matchedSku) {
        List<StockEntryVariant> entryVariants = variants.stream()
                .map(v -> new StockEntryVariant(v.getId(), v.getOptionLabel(), v.getSku(), v.getStockQuantity()))
                .toList();
        return new ProductStockEntryResponse(product.getId(), product.getName(), product.getPhotoKey() != null,
                matchedSku, entryVariants);
    }

    private List<String> parseKeywords(String rawKeywords) {
        if (rawKeywords == null) {
            return List.of();
        }
        return Arrays.stream(rawKeywords.split(","))
                .map(String::trim)
                .filter(keyword -> !keyword.isEmpty())
                .distinct()
                .toList();
    }

    @GetMapping("/products/{id}/edit")
    public String editForm(@AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long id, Model model) {
        Product product = productService.getOwned(principal.getMemberId(), id);
        model.addAttribute("form", ProductEditForm.from(product));
        model.addAttribute("productId", id);
        model.addAttribute("hasPhoto", product.getPhotoKey() != null);
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
        Product product = productService.getOwned(principal.getMemberId(), id);
        if (!bindingResult.hasErrors()) {
            try {
                productService.update(principal.getMemberId(), id, form);
                return "redirect:/products/" + id;
            } catch (InvalidPhotoException e) {
                bindingResult.rejectValue("photo", "invalid", e.getMessage());
            }
        }
        model.addAttribute("productId", id);
        model.addAttribute("hasPhoto", product.getPhotoKey() != null);
        return "products/edit";
    }

    @PostMapping("/products/{id}/delete")
    public String delete(@AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long id, Model model) {
        try {
            productService.delete(principal.getMemberId(), id);
            return "redirect:/products";
        } catch (ProductHasMovementsException e) {
            model.addAttribute("error", e.getMessage());
            return list(principal, null, model);
        }
    }
}
