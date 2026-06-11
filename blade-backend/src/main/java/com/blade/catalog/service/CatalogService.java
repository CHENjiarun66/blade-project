package com.blade.catalog.service;

import com.blade.catalog.dto.CatalogFiltersVO;
import com.blade.catalog.dto.CatalogPageDTO;
import com.blade.catalog.dto.CatalogProductVO;
import com.blade.common.result.PageResult;

public interface CatalogService {

    /**
     * Paginated product list for catalog display.
     */
    PageResult<CatalogProductVO> pageList(CatalogPageDTO dto);

    /**
     * Single product detail with SKUs, images, and stock status.
     */
    CatalogProductVO getById(Long id);

    /**
     * Available filter options: categories, colors, sizes, stockModes.
     */
    CatalogFiltersVO getFilters();
}
