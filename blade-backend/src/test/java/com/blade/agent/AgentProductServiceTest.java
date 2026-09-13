package com.blade.agent;

import com.blade.agent.dto.AgentProductDTO;
import com.blade.agent.service.AgentProductService;
import com.blade.product.dto.ProductCreateDTO;
import com.blade.product.dto.ProductVO;
import com.blade.product.entity.Product;
import com.blade.product.mapper.ProductMapper;
import com.blade.product.service.ProductService;
import com.blade.product.service.ProductCategoryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentProductServiceTest {
    private final ProductService productService = mock(ProductService.class);
    private final ProductMapper productMapper = mock(ProductMapper.class);
    private final ProductCategoryService categoryService = mock(ProductCategoryService.class);
    private final AgentProductService service = new AgentProductService(productService, categoryService, productMapper);

    @Test
    void duplicateProductCodeReturnsDuplicateWithoutMutation() {
        Product existing = new Product();
        existing.setId(8L);
        existing.setProductCode("7000#");
        when(productMapper.selectOne(any())).thenReturn(existing);
        ProductVO view = new ProductVO();
        view.setId(8L);
        view.setProductCode("7000#");
        view.setSkus(List.of());
        when(productService.getById(8L)).thenReturn(view);

        AgentProductDTO.CreateResult result = service.create(request("7000#", List.of(), List.of()));

        assertEquals("DUPLICATE", result.result());
        assertEquals(8L, result.productId());
        verify(productService, never()).create(any());
    }

    @Test
    void createResolvesExistingAttributeCodesAndNeverSetsCostOrInventory() {
        when(productMapper.selectOne(any())).thenReturn(null);
        ProductVO.ColorVO black = new ProductVO.ColorVO();
        black.setId(11L);
        black.setColorCode("BLACK");
        black.setStatus(1);
        ProductVO.SizeVO medium = new ProductVO.SizeVO();
        medium.setId(21L);
        medium.setSizeCode("M");
        medium.setStatus(1);
        when(productService.listAllColors()).thenReturn(List.of(black));
        when(productService.listAllSizes()).thenReturn(List.of(medium));
        when(productService.create(any())).thenReturn(31L);
        ProductVO created = new ProductVO();
        created.setId(31L);
        created.setProductCode("7001#");
        created.setSkus(List.of(new ProductVO.SkuVO(), new ProductVO.SkuVO()));
        when(productService.getById(31L)).thenReturn(created);

        AgentProductDTO.CreateResult result = service.create(request(" 7001# ", List.of("black"), List.of("m")));

        ArgumentCaptor<ProductCreateDTO> captor = ArgumentCaptor.forClass(ProductCreateDTO.class);
        verify(productService).create(captor.capture());
        ProductCreateDTO saved = captor.getValue();
        assertEquals(List.of(11L), saved.getColorIds());
        assertEquals(List.of(21L), saved.getSizeIds());
        assertEquals(null, saved.getCostPrice());
        assertEquals("CREATED", result.result());
        assertEquals(2, result.skuCount());
    }

    private AgentProductDTO.CreateRequest request(String code, List<String> colors, List<String> sizes) {
        return new AgentProductDTO.CreateRequest(code, "测试商品", null, "件",
                new BigDecimal("12.00"), null, null, null, colors, sizes);
    }
}
