package com.blade.agent;

import com.blade.agent.service.AgentCatalogService;
import com.blade.order.draft.dto.OrderDraftDTO.CatalogCandidate;
import com.blade.product.entity.Product;
import com.blade.product.entity.ProductColor;
import com.blade.product.entity.ProductSize;
import com.blade.product.entity.ProductSku;
import com.blade.product.mapper.ProductColorMapper;
import com.blade.product.mapper.ProductMapper;
import com.blade.product.mapper.ProductSizeMapper;
import com.blade.product.mapper.ProductSkuMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentCatalogPlaceholderTest {

    @Test
    void spuOnlyQueryPrefersPlaceholderButVariantQueryExcludesIt() {
        ProductSkuMapper skuMapper = mock(ProductSkuMapper.class);
        ProductMapper productMapper = mock(ProductMapper.class);
        ProductColorMapper colorMapper = mock(ProductColorMapper.class);
        ProductSizeMapper sizeMapper = mock(ProductSizeMapper.class);

        Product product = product(10L, "6000#");
        ProductSku black = sku(101L, 10L, 1L, 1L, "6000#-BLACK-M", "NORMAL");
        ProductSku white = sku(102L, 10L, 2L, 1L, "6000#-WHITE-M", "NORMAL");
        ProductSku placeholder = sku(199L, 10L, 9L, 9L, "6000#-UNSPEC-UNSPEC", "PLACEHOLDER");
        when(skuMapper.selectList(any())).thenReturn(List.of(black, white, placeholder));
        when(productMapper.selectBatchIds(any())).thenReturn(List.of(product));
        when(colorMapper.selectBatchIds(any())).thenReturn(List.of(
                color(1L, "BLACK", "黑色"), color(2L, "WHITE", "白色"), color(9L, "UNSPECIFIED", "未指定颜色")));
        when(sizeMapper.selectBatchIds(any())).thenReturn(List.of(size(1L, "M"), size(9L, "UNSPEC")));

        AgentCatalogService service = new AgentCatalogService(skuMapper, productMapper, colorMapper, sizeMapper);
        List<CatalogCandidate> spuMatches = service.search(null, "6000", null, null, 10);
        assertEquals(199L, spuMatches.get(0).getSkuId());
        assertTrue(spuMatches.get(0).isPlaceholder());
        assertEquals(new BigDecimal("1.00"), spuMatches.get(0).getMatchScore());

        List<CatalogCandidate> variantMatches = service.search(null, "6000", "黑", "M", 10);
        assertEquals(1, variantMatches.size());
        assertEquals(101L, variantMatches.get(0).getSkuId());
        assertFalse(variantMatches.get(0).isPlaceholder());
    }

    @Test
    void singleVariantSpuQueryStillPrefersPlaceholder() {
        ProductSkuMapper skuMapper = mock(ProductSkuMapper.class);
        ProductMapper productMapper = mock(ProductMapper.class);
        ProductColorMapper colorMapper = mock(ProductColorMapper.class);
        ProductSizeMapper sizeMapper = mock(ProductSizeMapper.class);

        Product product = product(20L, "7000#");
        ProductSku normal = sku(201L, 20L, 1L, 1L, "7000#-BLACK-S", "NORMAL");
        ProductSku placeholder = sku(299L, 20L, 9L, 9L, "7000#-UNSPECIFIED-UNSPEC", "PLACEHOLDER");
        when(skuMapper.selectList(any())).thenReturn(List.of(normal, placeholder));
        when(productMapper.selectBatchIds(any())).thenReturn(List.of(product));
        when(colorMapper.selectBatchIds(any())).thenReturn(List.of(
                color(1L, "BLACK", "黑色"), color(9L, "UNSPECIFIED", "未指定颜色")));
        when(sizeMapper.selectBatchIds(any())).thenReturn(List.of(size(1L, "S"), size(9L, "UNSPEC")));

        AgentCatalogService service = new AgentCatalogService(skuMapper, productMapper, colorMapper, sizeMapper);
        List<CatalogCandidate> matches = service.search(null, "7000", null, null, 10);

        assertEquals(299L, matches.get(0).getSkuId());
        assertTrue(matches.get(0).isPlaceholder());
        assertEquals(new BigDecimal("1.00"), matches.get(0).getMatchScore());
    }

    private Product product(Long id, String code) {
        Product row = new Product();
        row.setId(id);
        row.setProductCode(code);
        row.setName("测试款");
        row.setStatus(1);
        return row;
    }

    private ProductSku sku(Long id, Long productId, Long colorId, Long sizeId, String code, String type) {
        ProductSku row = new ProductSku();
        row.setId(id);
        row.setProductId(productId);
        row.setColorId(colorId);
        row.setSizeId(sizeId);
        row.setSkuCode(code);
        row.setSkuType(type);
        row.setPrice(new BigDecimal("68.00"));
        row.setStatus(1);
        return row;
    }

    private ProductColor color(Long id, String code, String name) {
        ProductColor row = new ProductColor();
        row.setId(id);
        row.setColorCode(code);
        row.setColorName(name);
        return row;
    }

    private ProductSize size(Long id, String code) {
        ProductSize row = new ProductSize();
        row.setId(id);
        row.setSizeCode(code);
        return row;
    }
}
