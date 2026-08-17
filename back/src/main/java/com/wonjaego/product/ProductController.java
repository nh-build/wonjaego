package com.wonjaego.product;

import com.wonjaego.ai.NameSuggestionClient;
import com.wonjaego.member.MemberPrincipal;
import com.wonjaego.movement.MovementService;
import com.wonjaego.storage.FileStorage;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
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
            try {
                productService.create(principal.getMemberId(), form);
                return "redirect:/products";
            } catch (InvalidPhotoException e) {
                bindingResult.rejectValue("photo", "invalid", e.getMessage());
            }
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
    public List<NameSuggestionResponse> suggestNames(@RequestBody NameSuggestionRequest request) {
        List<String> keywords = parseKeywords(request.keywords());
        if (keywords.isEmpty()) {
            throw new InvalidNameSuggestionRequestException("키워드를 입력해주세요.");
        }
        return nameSuggestionClient.suggest(keywords).stream()
                .map(NameSuggestionResponse::from)
                .toList();
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
            return list(principal, model);
        }
    }
}
