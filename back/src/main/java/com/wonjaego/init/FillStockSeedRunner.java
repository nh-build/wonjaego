package com.wonjaego.init;

import com.wonjaego.movement.MovementService;
import com.wonjaego.movement.MovementType;
import com.wonjaego.product.ProductVariant;
import com.wonjaego.product.ProductVariantRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Dev-only stock top-up for manual testing — never wired to run on its own (unlike
// BaseInitData): only fires when both dev profile AND wonjaego.seed.fill-stock=true are
// set, e.g. `./gradlew bootRun --args='--spring.profiles.active=dev
// --wonjaego.seed.fill-stock=true'`. Requires the extra property so an ordinary dev boot
// never silently re-tops every variant's stock behind the developer's back. @Order(2) so
// it always runs after BaseInitData (order 1) has had a chance to create its seed data.
@Component
@Profile("dev")
@Order(2)
@ConditionalOnProperty(name = "wonjaego.seed.fill-stock", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class FillStockSeedRunner implements ApplicationRunner {

    private static final int TARGET_STOCK = 100;
    private static final String FILL_MEMO = "초기 테스트 입고";

    private final ProductVariantRepository productVariantRepository;
    private final MovementService movementService;

    // ADR 0001 — stock is never set directly; every adjustment goes through a Movement so
    // it stays part of the same audit trail as a real 입고. Only the shortfall (100 minus
    // whatever a variant already has) is recorded, so re-running this against a variant
    // already at or above 100 is a no-op rather than pushing it past 100.
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<ProductVariant> variants = productVariantRepository.findAll();
        int filledCount = 0;
        for (ProductVariant variant : variants) {
            int deficit = TARGET_STOCK - variant.getStockQuantity();
            if (deficit <= 0) {
                continue;
            }
            movementService.record(variant.getMember().getId(), variant.getId(), null,
                    MovementType.INBOUND, deficit, FILL_MEMO);
            filledCount++;
        }

        long atTarget = productVariantRepository.findAll().stream()
                .filter(v -> v.getStockQuantity() == TARGET_STOCK)
                .count();
        log.info("[FillStockSeedRunner] 재고 100 채우기 완료 — 조정된 variant {}개, 현재 재고 100인 variant {}개 (전체 {}개)",
                filledCount, atTarget, variants.size());
    }
}
