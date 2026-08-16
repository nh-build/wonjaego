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
import java.math.BigDecimal;
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
        SalesChannel zigzag = salesChannelService.create(memberId, channelForm("지그재그"));

        Product tshirt = productService.create(memberId,
                productForm("베이직 반팔 티셔츠", "TSHIRT-001", new BigDecimal("19900"), 20, null));
        Product pants = productService.create(memberId,
                productForm("와이드 데님 팬츠", "PANTS-001", new BigDecimal("39900"), 10, 3));
        Product cardigan = productService.create(memberId,
                productForm("니트 가디건", "CARDIGAN-001", new BigDecimal("49900"), 5, null));
        Product bag = productService.create(memberId,
                productForm("레더 크로스백", "BAG-001", new BigDecimal("89000"), 8, null));

        movementService.record(memberId, tshirt.getId(), smartstore.getId(), MovementType.INBOUND, 10, "추가 입고");
        movementService.record(memberId, tshirt.getId(), smartstore.getId(), MovementType.SALE, 15, "스마트스토어 판매");

        movementService.record(memberId, pants.getId(), ably.getId(), MovementType.SALE, 8, "에이블리 판매");

        movementService.record(memberId, cardigan.getId(), zigzag.getId(), MovementType.RETURN, 2, "고객 반품");

        // newProductId=null means same-product exchange — recordExchange ignores quantity
        // entirely in that branch and always records a 0 delta, so pass 0 here to say so.
        movementService.recordExchange(memberId, bag.getId(), smartstore.getId(), null, 0, "사이즈 교환");
    }

    private SalesChannelForm channelForm(String name) {
        SalesChannelForm form = new SalesChannelForm();
        form.setName(name);
        return form;
    }

    private ProductCreateForm productForm(String name, String sku, BigDecimal price, int stockQuantity, Integer lowStockThreshold) {
        ProductCreateForm form = new ProductCreateForm();
        form.setName(name);
        form.setSku(sku);
        form.setPrice(price);
        form.setStockQuantity(stockQuantity);
        form.setLowStockThreshold(lowStockThreshold);
        return form;
    }
}
