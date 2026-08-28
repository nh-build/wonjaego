package com.wonjaego.product;

import java.util.List;

// One page of /products — served both as the initial SSR render (page 0) and as the JSON
// shape the infinite-scroll JS fetches for every page after that (same endpoint shape,
// so there's exactly one place that computes filtering/sorting/pagination).
public record ProductListPage(List<ProductListItem> items, long totalCount, boolean hasNext, long importedCount) {
}
