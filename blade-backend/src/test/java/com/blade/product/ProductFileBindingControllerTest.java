package com.blade.product;

import com.blade.product.controller.ProductController;
import com.blade.product.dto.ProductFileBindingDTO;
import com.blade.product.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductFileBindingControllerTest {

    private ProductService productService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        productService = mock(ProductService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ProductController(productService)).build();
    }

    @Test
    void bindFiles_passesPathIdAndDtoToService() throws Exception {
        String body = """
                {
                  "mainFileId": 100,
                  "galleryFileIds": [200, 201],
                  "skuImageBindings": [
                    { "skuId": 10, "fileIds": [300, 301] }
                  ]
                }
                """;

        mockMvc.perform(put("/api/products/9/file-bindings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<ProductFileBindingDTO> captor = ArgumentCaptor.forClass(ProductFileBindingDTO.class);
        verify(productService).bindFiles(eq(9L), captor.capture());

        ProductFileBindingDTO dto = captor.getValue();
        assertThat(dto.getMainFileId()).isEqualTo(100L);
        assertThat(dto.getGalleryFileIds()).containsExactly(200L, 201L);
        assertThat(dto.getSkuImageBindings()).hasSize(1);
        assertThat(dto.getSkuImageBindings().get(0).getSkuId()).isEqualTo(10L);
        assertThat(dto.getSkuImageBindings().get(0).getFileIds()).containsExactly(300L, 301L);
    }

    @Test
    void bindFiles_passesEmptyGalleryListForClearSemantics() throws Exception {
        mockMvc.perform(put("/api/products/9/file-bindings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"galleryFileIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<ProductFileBindingDTO> captor = ArgumentCaptor.forClass(ProductFileBindingDTO.class);
        verify(productService).bindFiles(eq(9L), captor.capture());

        assertThat(captor.getValue().getGalleryFileIds()).isEmpty();
    }
}
