package com.blade.catalog;

import com.blade.catalog.dto.CatalogPageDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Catalog DTOs (no Spring context needed).
 */
class CatalogDtoTest {

    @Test
    void pageDTO_pageAlias_mapsToCurrent() {
        CatalogPageDTO dto = new CatalogPageDTO();
        dto.setPage(3L);
        assertEquals(3L, dto.getCurrent());
        assertEquals(2L * dto.getSize(), dto.offset()); // offset = (3-1)*20 = 40
    }

    @Test
    void pageDTO_defaultCurrent_isOne() {
        CatalogPageDTO dto = new CatalogPageDTO();
        assertEquals(1L, dto.getCurrent());
        assertEquals(0L, dto.offset());
    }

    @Test
    void pageDTO_size_defaultsTo20() {
        CatalogPageDTO dto = new CatalogPageDTO();
        assertEquals(20L, dto.getSize());
    }

    @Test
    void pageDTO_normalize_clampsInvalidPagination() {
        CatalogPageDTO dto = new CatalogPageDTO();
        dto.setCurrent(0L);
        dto.setSize(200L);
        dto.normalize();

        assertEquals(1L, dto.getCurrent());
        assertEquals(100L, dto.getSize());
        assertEquals("all", dto.getStockMode());
    }
}
