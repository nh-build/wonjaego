package com.wonjaego.product;

import com.wonjaego.member.MemberPrincipal;
import com.wonjaego.member.MemberService;
import com.wonjaego.movement.InvalidStockMovementException;
import com.wonjaego.movement.MovementService;
import com.wonjaego.movement.MovementType;
import com.wonjaego.storage.FileStorage;
import jakarta.validation.Valid;
import java.util.ArrayList;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductVariantService productVariantService;
    private final MovementService movementService;
    private final FileStorage fileStorage;
    private final NameSuggestionService nameSuggestionService;
    private final MemberService memberService;

    // 등록 전용 화면 — 상품 목록과 분리(등록은 등록에만 집중). GET /products는 목록 화면이다.
    // 등록/수정 화면은 같은 폼 템플릿(products/product-form)을 공유한다 — 이 속성들이 그
    // 템플릿이 두 모드를 구분하는 데 쓰는 전부다.
    @GetMapping("/products/new")
    public String newForm(Model model) {
        ProductCreateForm form = new ProductCreateForm();
        form.setAutoGenerateBarcode(true);
        model.addAttribute("form", form);
        addCreateFormAttributes(model);
        return "products/new";
    }

    private void addCreateFormAttributes(Model model) {
        model.addAttribute("isEdit", false);
        model.addAttribute("hasPhoto", false);
        model.addAttribute("productId", null);
        model.addAttribute("variantSeeds", List.of());
        model.addAttribute("formAction", "/products");
        model.addAttribute("submitLabel", "등록하기");
    }

    private void addEditFormAttributes(Model model, Long productId, boolean hasPhoto, List<ProductEditVariantSeed> variantSeeds) {
        model.addAttribute("isEdit", true);
        model.addAttribute("hasPhoto", hasPhoto);
        model.addAttribute("productId", productId);
        model.addAttribute("variantSeeds", variantSeeds);
        model.addAttribute("formAction", "/products/" + productId + "/edit");
        model.addAttribute("submitLabel", "수정하기");
    }

    @GetMapping("/products")
    public String list(@AuthenticationPrincipal MemberPrincipal principal,
                        @RequestParam(required = false) String stock,
                        @RequestParam(required = false) String q,
                        @RequestParam(required = false) String sort,
                        Model model) {
        model.addAttribute("page", productService.listPage(principal.getMemberId(), q, stock, sort, 0));
        model.addAttribute("stockFilter", stock);
        model.addAttribute("query", q);
        model.addAttribute("sort", sort);
        return "products/list";
    }

    // Backs the list screen's infinite scroll (page 0 is already server-rendered by list()
    // above) — same filter/sort/pagination logic, one shared source (ProductService.listPage).
    @GetMapping("/products/list-page")
    @ResponseBody
    public ProductListPage listPage(@AuthenticationPrincipal MemberPrincipal principal,
                                     @RequestParam(required = false) String stock,
                                     @RequestParam(required = false) String q,
                                     @RequestParam(required = false) String sort,
                                     @RequestParam(defaultValue = "0") int page) {
        return productService.listPage(principal.getMemberId(), q, stock, sort, page);
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
            } catch (InvalidStockDataException | InvalidPriceDataException | InvalidBarcodeDataException e) {
                bindingResult.reject("invalid", e.getMessage());
            }
        }
        addCreateFormAttributes(model);
        return "products/new";
    }

    @GetMapping("/products/{id}")
    public String detail(@AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long id, Model model) {
        model.addAttribute("product", productService.getOwned(principal.getMemberId(), id));
        List<ProductVariant> variants = productVariantService.listForProduct(principal.getMemberId(), id);
        model.addAttribute("variants", variants);
        model.addAttribute("movements", movementService.listForProduct(principal.getMemberId(), id));
        model.addAttribute("totalStock", variants.stream().mapToInt(ProductVariant::getStockQuantity).sum());
        model.addAttribute("barcodeCount", variants.stream().filter(v -> v.getBarcode() != null).count());
        model.addAttribute("lowStockThreshold", memberService.getLowStockThreshold(principal.getMemberId()));
        return "products/detail";
    }

    // Backs the detail screen's 옵션별 재고 stepper/direct-entry "변경사항 저장" bar — the
    // client already resolved each changed variant to exactly one movement (stepper click
    // vs. typed edit, see MovementService.QUICK_ADJUST_TYPES) before posting.
    @PostMapping("/products/{id}/stock-adjustments")
    @ResponseBody
    public ResponseEntity<Map<String, String>> stockAdjustments(@AuthenticationPrincipal MemberPrincipal principal,
                                                                  @PathVariable Long id,
                                                                  @RequestBody StockAdjustmentRequest request) {
        List<MovementService.StockAdjustmentEntry> entries = new ArrayList<>();
        for (StockAdjustmentRequest.Entry entry : request.getEntries()) {
            MovementType type;
            try {
                type = MovementType.valueOf(entry.getType());
            } catch (IllegalArgumentException | NullPointerException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "허용되지 않는 조정 사유입니다."));
            }
            entries.add(new MovementService.StockAdjustmentEntry(entry.getVariantId(), type, entry.getQuantity()));
        }
        try {
            movementService.recordQuickAdjustments(principal.getMemberId(), id, entries);
        } catch (InvalidStockMovementException | InsufficientStockException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        return ResponseEntity.ok(Map.of());
    }

    // 옵션별 바코드 리스트 화면 — view/print only (barcode generation stays a
    // registration-time-only action, see products/new.html).
    @GetMapping("/products/{id}/barcodes")
    public String barcodes(@AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long id, Model model) {
        model.addAttribute("product", productService.getOwned(principal.getMemberId(), id));
        model.addAttribute("variants", productVariantService.listForProduct(principal.getMemberId(), id));
        return "products/barcodes";
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
    public List<String> suggestNames(@RequestParam(value = "photo", required = false) MultipartFile photo,
                                      @RequestParam(value = "category", required = false) String category) {
        return nameSuggestionService.suggest(photo, category);
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
                .map(product -> new ProductSearchResult(product.getId(), product.getName(), product.resolveImageUrl()))
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
        return new ProductStockEntryResponse(product.getId(), product.getName(), product.resolveImageUrl(),
                matchedSku, entryVariants);
    }

    @GetMapping("/products/{id}/edit")
    public String editForm(@AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long id, Model model) {
        Product product = productService.getOwned(principal.getMemberId(), id);
        List<ProductVariant> variants = productVariantService.listForProduct(principal.getMemberId(), id);
        model.addAttribute("form", ProductEditForm.from(product, variants));
        addEditFormAttributes(model, id, product.getPhotoKey() != null, toEditVariantSeeds(variants));
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
            } catch (InvalidPriceDataException | InvalidStockDataException | VariantHasMovementsException e) {
                bindingResult.reject("invalid", e.getMessage());
            }
        }
        addEditFormAttributes(model, id, product.getPhotoKey() != null,
                toEditVariantSeeds(productVariantService.listForProduct(principal.getMemberId(), id)));
        return "products/edit";
    }

    private List<ProductEditVariantSeed> toEditVariantSeeds(List<ProductVariant> variants) {
        return variants.stream()
                .map(v -> new ProductEditVariantSeed(
                        v.getId(),
                        v.getOptionLabel(),
                        v.getOptionValues().stream()
                                .collect(Collectors.toMap(ov -> ov.getOptionGroup().getId(), OptionValue::getValue)),
                        v.getPrice(),
                        v.getStockQuantity(),
                        v.getBarcode() != null))
                .toList();
    }

    // ProductHasMovementsException bounces the seller back to the detail page they clicked
    // delete from (via a flash attribute, ChannelController's established pattern) rather
    // than to the product list — the error is only meaningful in the context they acted in.
    @PostMapping("/products/{id}/delete")
    public String delete(@AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long id,
                          RedirectAttributes redirectAttributes) {
        try {
            productService.delete(principal.getMemberId(), id);
            return "redirect:/products";
        } catch (ProductHasMovementsException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/products/" + id;
        }
    }
}
