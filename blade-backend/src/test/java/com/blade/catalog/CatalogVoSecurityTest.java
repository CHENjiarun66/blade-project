package com.blade.catalog;

import com.blade.catalog.dto.CatalogProductVO;
import com.blade.catalog.dto.CatalogSkuVO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that forbidden fields (cost, supplier, raw inventory quantities, etc.)
 * are absent from catalog VO classes.
 */
class CatalogVoSecurityTest {

    private static final Set<String> FORBIDDEN_PATTERNS = Set.of(
        "cost", "Cost", "price", "Price",
        "supplier", "Supplier",
        "quantity", "Quantity",
        "reservedQty", "reserved_qty",
        "globalReserved", "global_reserved",
        "tenant", "Tenant",
        "deleted", "Deleted",
        "customer", "Customer"
    );

    private static final Set<String> ALLOWED_PRODUCT_FIELDS = Set.of(
        "id", "productCode", "name",
        "categoryId", "categoryName",
        "mainImageUrl", "imageUrls",
        "hasImage", "hasStock", "stockStatus",
        "tags", "colors", "sizes", "skus",
        "createTime"
    );

    private static final Set<String> ALLOWED_SKU_FIELDS = Set.of(
        "id", "skuCode",
        "colorId", "colorName",
        "sizeId", "sizeCode",
        "imageUrls",
        "hasStock", "stockStatus"
    );

    @Test
    void catalogProductVO_shouldNotExposeForbiddenFields() {
        Set<String> fieldNames = Arrays.stream(CatalogProductVO.class.getDeclaredFields())
            .map(Field::getName)
            .collect(Collectors.toSet());

        for (String fn : fieldNames) {
            for (String forbidden : FORBIDDEN_PATTERNS) {
                assertFalse(fn.toLowerCase().contains(forbidden.toLowerCase()),
                    "CatalogProductVO must not expose field '" + fn + "' (matches forbidden pattern '" + forbidden + "')");
            }
        }
    }

    @Test
    void catalogProductVO_shouldOnlyHaveAllowedFields() {
        Set<String> fieldNames = Arrays.stream(CatalogProductVO.class.getDeclaredFields())
            .map(Field::getName)
            .collect(Collectors.toSet());

        // inner class ColorSizeEntry is fine
        fieldNames.remove("this$0");

        for (String fn : fieldNames) {
            if (fn.startsWith("$")) continue; // skip synthetic fields
            assertTrue(ALLOWED_PRODUCT_FIELDS.contains(fn),
                "CatalogProductVO has unexpected field '" + fn + "'. Only allowed: " + ALLOWED_PRODUCT_FIELDS);
        }
    }

    @Test
    void catalogSkuVO_shouldNotExposeForbiddenFields() {
        Set<String> fieldNames = Arrays.stream(CatalogSkuVO.class.getDeclaredFields())
            .map(Field::getName)
            .collect(Collectors.toSet());

        for (String fn : fieldNames) {
            if (fn.startsWith("$")) continue;
            for (String forbidden : FORBIDDEN_PATTERNS) {
                assertFalse(fn.toLowerCase().contains(forbidden.toLowerCase()),
                    "CatalogSkuVO must not expose field '" + fn + "' (matches forbidden pattern '" + forbidden + "')");
            }
        }
    }

    @Test
    void catalogSkuVO_shouldOnlyHaveAllowedFields() {
        Set<String> fieldNames = Arrays.stream(CatalogSkuVO.class.getDeclaredFields())
            .map(Field::getName)
            .collect(Collectors.toSet());

        for (String fn : fieldNames) {
            if (fn.startsWith("$")) continue;
            assertTrue(ALLOWED_SKU_FIELDS.contains(fn),
                "CatalogSkuVO has unexpected field '" + fn + "'. Only allowed: " + ALLOWED_SKU_FIELDS);
        }
    }
}
