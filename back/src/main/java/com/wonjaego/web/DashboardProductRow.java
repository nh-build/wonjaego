package com.wonjaego.web;

public record DashboardProductRow(Long id, String name, boolean hasPhoto, int totalStock, boolean lowStock) {
}
