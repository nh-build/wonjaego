package com.wonjaego.movement;

import com.wonjaego.channel.SalesChannel;
import com.wonjaego.channel.SalesChannelService;
import com.wonjaego.product.InsufficientStockException;
import com.wonjaego.product.ProductService;
import com.wonjaego.product.ProductVariant;
import com.wonjaego.product.ProductVariantService;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MovementService {

    // Doubles as each screen's allowed-reason set: every type a screen accepts has exactly
    // that screen's sign, so 입고하기 accepts POSITIVE_TYPES and 출고하기 accepts NEGATIVE_TYPES —
    // one set per direction, not a separate (and easily desynced) pair per concern.
    private static final Set<MovementType> POSITIVE_TYPES =
            EnumSet.of(MovementType.INBOUND, MovementType.RETURN, MovementType.ADJUSTMENT_IN);
    private static final Set<MovementType> NEGATIVE_TYPES =
            EnumSet.of(MovementType.SALE, MovementType.DISPOSAL, MovementType.EXCHANGE_OUT, MovementType.ADJUSTMENT_OUT);

    private final MovementRepository movementRepository;
    private final ProductService productService;
    private final ProductVariantService productVariantService;
    private final SalesChannelService salesChannelService;

    @Transactional(readOnly = true)
    public List<Movement> listForProduct(Long memberId, Long productId) {
        productService.getOwned(memberId, productId);
        return movementRepository.findAllByProductIdWithChannelAndVariant(productId);
    }

    // salesChannelId may be null — the 입고하기/출고하기 batch screens record movements with
    // no channel, same as registration-time initial stock (ADR 0006).
    @Transactional
    public Movement record(Long memberId, Long variantId, Long salesChannelId, MovementType type, int quantity, String memo) {
        ProductVariant variant = productVariantService.getOwned(memberId, variantId);
        SalesChannel channel = salesChannelId != null ? salesChannelService.getOwned(memberId, salesChannelId) : null;

        int quantityChange = signedQuantity(type, quantity);

        variant.adjustStock(quantityChange);
        Movement movement = new Movement(variant, channel, type, quantityChange, memo);
        return movementRepository.save(movement);
    }

    // Records one INBOUND/RETURN/ADJUSTMENT_IN movement per variant in quantitiesByVariantId
    // (skipping zero entries), no channel. Used by the 입고하기 screen.
    @Transactional
    public void recordStockIn(Long memberId, MovementType type, Map<Long, Integer> quantitiesByVariantId, String memo) {
        if (!POSITIVE_TYPES.contains(type)) {
            throw new InvalidStockMovementException("입고 화면에서 사용할 수 없는 사유입니다.");
        }
        recordBatch(memberId, type, quantitiesByVariantId, memo);
    }

    // Records one SALE/DISPOSAL/EXCHANGE_OUT/ADJUSTMENT_OUT movement per variant in
    // quantitiesByVariantId (skipping zero entries), no channel. Used by the 출고하기 screen.
    @Transactional
    public void recordStockOut(Long memberId, MovementType type, Map<Long, Integer> quantitiesByVariantId, String memo) {
        if (!NEGATIVE_TYPES.contains(type)) {
            throw new InvalidStockMovementException("출고 화면에서 사용할 수 없는 사유입니다.");
        }
        recordBatch(memberId, type, quantitiesByVariantId, memo);
    }

    private void recordBatch(Long memberId, MovementType type, Map<Long, Integer> quantitiesByVariantId, String memo) {
        // Resolve+filter first, then (for outbound types) validate stock sufficiency across
        // the WHOLE batch before adjusting anything — a shortage on the 3rd combo must not
        // leave the first two already mutated in a transaction that then rolls back (a test
        // sharing this transaction would otherwise see the not-yet-rolled-back adjustment;
        // same reasoning as ProductService.create()'s validate-before-write comment).
        Map<ProductVariant, Integer> quantitiesByVariant = new LinkedHashMap<>();
        for (Map.Entry<Long, Integer> entry : quantitiesByVariantId.entrySet()) {
            Integer quantity = entry.getValue();
            if (quantity == null || quantity < 0) {
                throw new InvalidStockMovementException("수량은 0 이상의 숫자여야 합니다.");
            }
            if (quantity == 0) {
                continue;
            }
            quantitiesByVariant.put(productVariantService.getOwned(memberId, entry.getKey()), quantity);
        }
        if (quantitiesByVariant.isEmpty()) {
            throw new InvalidStockMovementException("수량을 하나 이상 입력해주세요.");
        }

        if (NEGATIVE_TYPES.contains(type)) {
            for (Map.Entry<ProductVariant, Integer> entry : quantitiesByVariant.entrySet()) {
                if (entry.getValue() > entry.getKey().getStockQuantity()) {
                    throw new InsufficientStockException(entry.getKey().getDisplayName());
                }
            }
        }

        List<Movement> movements = new ArrayList<>();
        for (Map.Entry<ProductVariant, Integer> entry : quantitiesByVariant.entrySet()) {
            int quantityChange = signedQuantity(type, entry.getValue());
            entry.getKey().adjustStock(quantityChange);
            movements.add(new Movement(entry.getKey(), null, type, quantityChange, memo));
        }
        movementRepository.saveAll(movements);
    }

    private int signedQuantity(MovementType type, int quantity) {
        if (POSITIVE_TYPES.contains(type)) {
            return quantity;
        }
        if (NEGATIVE_TYPES.contains(type)) {
            return -quantity;
        }
        throw new IllegalArgumentException(type + "는 signedQuantity()로 부호를 판단할 수 없습니다 — EXCHANGE는 recordExchange()를 쓰세요.");
    }

    @Transactional
    public void recordExchange(Long memberId, Long originalVariantId, Long salesChannelId, Long newVariantId,
                                int quantity, String memo) {
        ProductVariant originalVariant = productVariantService.getOwned(memberId, originalVariantId);
        SalesChannel channel = salesChannelService.getOwned(memberId, salesChannelId);

        if (newVariantId == null || newVariantId.equals(originalVariantId)) {
            movementRepository.save(new Movement(originalVariant, channel, MovementType.EXCHANGE, 0, memo));
            return;
        }

        ProductVariant newVariant = productVariantService.getOwned(memberId, newVariantId);

        // newVariant's adjustment is the only one that can fail (it's the only negative
        // delta), so it must run first — otherwise a shortage on newVariant would leave
        // originalVariant already mutated in-memory before the exception unwinds.
        newVariant.adjustStock(-quantity);
        originalVariant.adjustStock(quantity);

        movementRepository.save(new Movement(originalVariant, channel, MovementType.EXCHANGE, quantity, memo));
        movementRepository.save(new Movement(newVariant, channel, MovementType.EXCHANGE, -quantity, memo));
    }
}
