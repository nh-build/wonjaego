package com.wonjaego.init;

import com.wonjaego.channel.SalesChannel;
import com.wonjaego.channel.SalesChannelForm;
import com.wonjaego.channel.SalesChannelService;
import com.wonjaego.member.Member;
import com.wonjaego.member.MemberRepository;
import com.wonjaego.member.MemberService;
import com.wonjaego.movement.MovementService;
import com.wonjaego.movement.MovementType;
import com.wonjaego.product.Product;
import com.wonjaego.product.ProductCreateForm;
import com.wonjaego.product.ProductService;
import com.wonjaego.product.ProductVariant;
import com.wonjaego.product.ProductVariantEditForm;
import com.wonjaego.product.ProductVariantService;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class BaseInitData implements ApplicationRunner {

    private final MemberRepository memberRepository;
    private final MemberService memberService;
    private final SalesChannelService salesChannelService;
    private final ProductService productService;
    private final ProductVariantService productVariantService;
    private final MovementService movementService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (memberRepository.count() > 0) {
            return;
        }

        Member member = memberService.signUp("seller", "password123", "원재고 샘플가게");
        Long memberId = member.getId();

        SalesChannel smartstore = salesChannelService.create(memberId, channelForm("스마트스토어"));
        SalesChannel ably = salesChannelService.create(memberId, channelForm("에이블리"));
        salesChannelService.create(memberId, channelForm("지그재그"));

        Product tshirt = productService.create(memberId,
                productForm("베이직 반팔 티셔츠", new BigDecimal("19900"),
                        optionGroup("색상", "블랙, 화이트"), optionGroup("사이즈", "S, M")));
        Product bag = productService.create(memberId,
                productForm("레더 크로스백", new BigDecimal("89000")));

        // The cartesian product generates 블랙/S, 블랙/M, 화이트/S, 화이트/M in that order.
        ProductVariant blackSmall = productVariantService.listForProduct(memberId, tshirt.getId()).get(0);
        ProductVariant bagVariant = productVariantService.listForProduct(memberId, bag.getId()).get(0);

        movementService.record(memberId, blackSmall.getId(), smartstore.getId(), MovementType.INBOUND, 20, "초기 입고");
        movementService.record(memberId, blackSmall.getId(), smartstore.getId(), MovementType.SALE, 15, "스마트스토어 판매");

        // Stock ends at 5, at/under this explicit threshold of 6 — shows up in the
        // dashboard's low-stock list, and also demonstrates a variant with its SKU and
        // threshold filled in (vs. every other seeded variant, left at their defaults).
        productVariantService.update(memberId, blackSmall.getId(), variantEditForm("TSHIRT-BLACK-S", 6));

        movementService.record(memberId, bagVariant.getId(), ably.getId(), MovementType.INBOUND, 8, "초기 입고");
        movementService.record(memberId, bagVariant.getId(), ably.getId(), MovementType.RETURN, 1, "고객 반품");

        // newVariantId=null means same-variant exchange — recordExchange ignores quantity
        // entirely in that branch and always records a 0 delta, so pass 0 here to say so.
        movementService.recordExchange(memberId, bagVariant.getId(), ably.getId(), null, 0, "사이즈 교환");
    }

    private SalesChannelForm channelForm(String name) {
        SalesChannelForm form = new SalesChannelForm();
        form.setName(name);
        return form;
    }

    private ProductVariantEditForm variantEditForm(String sku, Integer lowStockThreshold) {
        ProductVariantEditForm form = new ProductVariantEditForm();
        form.setSku(sku);
        form.setLowStockThreshold(lowStockThreshold);
        return form;
    }

    private ProductCreateForm.OptionGroupInput optionGroup(String name, String valuesText) {
        ProductCreateForm.OptionGroupInput input = new ProductCreateForm.OptionGroupInput();
        input.setName(name);
        input.setValuesText(valuesText);
        return input;
    }

    private ProductCreateForm productForm(String name, BigDecimal price, ProductCreateForm.OptionGroupInput... optionGroups) {
        ProductCreateForm form = new ProductCreateForm();
        form.setName(name);
        form.setPrice(price);
        form.setOptionGroups(List.of(optionGroups));
        return form;
    }
}
