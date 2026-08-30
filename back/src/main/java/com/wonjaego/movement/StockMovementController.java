package com.wonjaego.movement;

import com.wonjaego.member.MemberPrincipal;
import com.wonjaego.product.InsufficientStockException;
import com.wonjaego.product.ProductService;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

// The templates re-fetch the selected product's combo table client-side via
// /products/{id}/stock-entry (and /products/search, /products/by-sku) rather than the
// server hydrating it — this controller only needs to preselect productId/type and
// enforce ownership.
@Controller
@RequiredArgsConstructor
public class StockMovementController {

    private final MovementService movementService;
    private final ProductService productService;

    // 재고 탭 — 입고/출고 진입점 + 최근 재고 이력. Distinct from /movements/new (single-variant
    // form MovementController still owns) and from /movements/stock-in|out (this class's own
    // batch entry forms) — this is the tab's landing page, not a form submission target.
    @GetMapping("/movements")
    public String stockTab(@AuthenticationPrincipal MemberPrincipal principal, Model model) {
        model.addAttribute("recentMovements", movementService.listRecent(principal.getMemberId()));
        return "movements/index";
    }

    @GetMapping("/movements/stock-in")
    public String stockInForm(@AuthenticationPrincipal MemberPrincipal principal,
                               @RequestParam(required = false) Long productId,
                               Model model) {
        return renderForm(principal, productId, "movements/stock-in", model);
    }

    @GetMapping("/movements/stock-out")
    public String stockOutForm(@AuthenticationPrincipal MemberPrincipal principal,
                                @RequestParam(required = false) Long productId,
                                Model model) {
        return renderForm(principal, productId, "movements/stock-out", model);
    }

    @PostMapping("/movements/stock-in")
    public String stockIn(@AuthenticationPrincipal MemberPrincipal principal,
                           @Valid @ModelAttribute("form") StockMovementForm form,
                           BindingResult bindingResult,
                           Model model) {
        return process(principal, form, bindingResult, model, "movements/stock-in", true);
    }

    @PostMapping("/movements/stock-out")
    public String stockOut(@AuthenticationPrincipal MemberPrincipal principal,
                            @Valid @ModelAttribute("form") StockMovementForm form,
                            BindingResult bindingResult,
                            Model model) {
        return process(principal, form, bindingResult, model, "movements/stock-out", false);
    }

    private String renderForm(MemberPrincipal principal, Long productId, String viewName, Model model) {
        if (productId != null) {
            productService.getOwned(principal.getMemberId(), productId);
        }
        StockMovementForm form = new StockMovementForm();
        form.setProductId(productId);
        model.addAttribute("form", form);
        return viewName;
    }

    private String process(MemberPrincipal principal, StockMovementForm form, BindingResult bindingResult,
                            Model model, String viewName, boolean isStockIn) {
        // Ownership must 404 regardless of validation outcome on the other fields, not just
        // as a side effect of the movementService call below.
        if (form.getProductId() != null) {
            productService.getOwned(principal.getMemberId(), form.getProductId());
        }
        if (!bindingResult.hasErrors()) {
            try {
                // No merge function — a duplicate variantId (malformed/tampered request;
                // the generated form never produces one) must fail loudly, not silently
                // drop one of the two submitted quantities.
                Map<Long, Integer> quantities = form.getEntries().stream()
                        .collect(Collectors.toMap(StockMovementForm.QuantityEntry::getVariantId,
                                StockMovementForm.QuantityEntry::getQuantity));
                if (isStockIn) {
                    movementService.recordStockIn(principal.getMemberId(), form.getType(), quantities, form.getMemo());
                } else {
                    movementService.recordStockOut(principal.getMemberId(), form.getType(), quantities, form.getMemo());
                }
                return "redirect:/products/" + form.getProductId();
            } catch (InsufficientStockException | InvalidStockMovementException e) {
                bindingResult.reject("invalid", e.getMessage());
            } catch (IllegalStateException e) {
                bindingResult.reject("invalid", "중복된 옵션 조합이 있습니다.");
            }
        }
        model.addAttribute("form", form);
        return viewName;
    }
}
