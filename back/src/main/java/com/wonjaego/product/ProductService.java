package com.wonjaego.product;

import com.wonjaego.member.Member;
import com.wonjaego.member.MemberRepository;
import com.wonjaego.movement.MovementRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final MemberRepository memberRepository;
    private final MovementRepository movementRepository;
    private final OptionGroupRepository optionGroupRepository;
    private final OptionValueRepository optionValueRepository;
    private final ProductVariantRepository productVariantRepository;

    @Transactional(readOnly = true)
    public List<Product> listOwned(Long memberId) {
        return productRepository.findAllByMemberId(memberId);
    }

    @Transactional(readOnly = true)
    public Product getOwned(Long memberId, Long productId) {
        return productRepository.findByIdAndMemberId(productId, memberId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    @Transactional
    public Product create(Long memberId, ProductCreateForm form) {
        Member member = memberRepository.getReferenceById(memberId);
        Product product = productRepository.save(new Product(member, form.getName(), form.getPrice()));

        List<List<OptionValue>> groupsOfValues = new ArrayList<>();
        for (ProductCreateForm.OptionGroupInput input : form.getOptionGroups()) {
            String groupName = input.getName() == null ? "" : input.getName().trim();
            String valuesText = input.getValuesText() == null ? "" : input.getValuesText().trim();
            if (groupName.isEmpty() || valuesText.isEmpty()) {
                continue;
            }
            List<String> distinctValues = Arrays.stream(valuesText.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .distinct()
                    .toList();
            // A comma-only input (e.g. ",,,") is non-blank but parses to zero values — skip
            // it like an unused slot, since an OptionGroup with no values would collapse the
            // whole cartesian product to nothing, leaving the product with no variants at all.
            if (distinctValues.isEmpty()) {
                continue;
            }
            OptionGroup group = optionGroupRepository.save(new OptionGroup(product, groupName));
            List<OptionValue> values = distinctValues.stream()
                    .map(value -> optionValueRepository.save(new OptionValue(group, value)))
                    .toList();
            groupsOfValues.add(values);
        }

        for (Set<OptionValue> combination : cartesianProduct(groupsOfValues)) {
            productVariantRepository.save(new ProductVariant(product, combination));
        }

        return product;
    }

    @Transactional
    public Product update(Long memberId, Long productId, ProductEditForm form) {
        Product product = getOwned(memberId, productId);
        product.updateInfo(form.getName(), form.getPrice());
        return product;
    }

    @Transactional
    public void delete(Long memberId, Long productId) {
        Product product = getOwned(memberId, productId);
        if (movementRepository.existsByVariant_ProductId(productId)) {
            throw new ProductHasMovementsException(product.getName());
        }
        productVariantRepository.deleteAllByProductId(productId);
        optionValueRepository.deleteAllByOptionGroup_ProductId(productId);
        optionGroupRepository.deleteAllByProductId(productId);
        productRepository.delete(product);
    }

    // Cartesian product of each option group's values. No groups -> one empty combination
    // (the product itself is the only variant), matching the "0 options = single unit" rule.
    private List<Set<OptionValue>> cartesianProduct(List<List<OptionValue>> groupsOfValues) {
        List<Set<OptionValue>> combinations = new ArrayList<>();
        combinations.add(new LinkedHashSet<>());
        for (List<OptionValue> values : groupsOfValues) {
            List<Set<OptionValue>> next = new ArrayList<>();
            for (Set<OptionValue> partial : combinations) {
                for (OptionValue value : values) {
                    Set<OptionValue> combination = new LinkedHashSet<>(partial);
                    combination.add(value);
                    next.add(combination);
                }
            }
            combinations = next;
        }
        return combinations;
    }
}
