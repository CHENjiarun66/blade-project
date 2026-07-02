package com.blade.catalog;

import com.blade.auth.dto.LoginRequest;
import com.blade.auth.dto.LoginResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Catalog API endpoints.
 * Tests that:
 * 1. Authentication is required
 * 2. DTO params reach the service
 * 3. Filters endpoint returns expected structure
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setTenantCode("super_admin");
        loginRequest.setUsername("admin");
        loginRequest.setPassword("admin123");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        LoginResponse loginResponse = objectMapper.readValue(response, LoginResponse.class);
        adminToken = loginResponse.getToken();
        assertNotNull(adminToken);
    }

    // ── Authentication ──

    @Test
    void listProducts_requiresAuth() throws Exception {
        mockMvc.perform(get("/api/catalog/products"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getProduct_requiresAuth() throws Exception {
        mockMvc.perform(get("/api/catalog/products/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getFilters_requiresAuth() throws Exception {
        mockMvc.perform(get("/api/catalog/filters"))
                .andExpect(status().isForbidden());
    }

    // ── Product list ──

    @Test
    void listProducts_withAuth_returnsOk() throws Exception {
        mockMvc.perform(get("/api/catalog/products")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    void listProducts_withKeyword_returnsOk() throws Exception {
        mockMvc.perform(get("/api/catalog/products")
                .param("keyword", "test")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void listProducts_withPagination_returnsOk() throws Exception {
        mockMvc.perform(get("/api/catalog/products")
                .param("current", "1")
                .param("size", "5")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.size").value(5))
                .andExpect(jsonPath("$.data.current").value(1));
    }

    @Test
    void listProducts_withPageAlias_returnsOk() throws Exception {
        mockMvc.perform(get("/api/catalog/products")
                .param("page", "2")
                .param("size", "10")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void listProducts_withFilters_returnsOk() throws Exception {
        mockMvc.perform(get("/api/catalog/products")
                .param("stockMode", "in_stock")
                .param("hasImage", "true")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ── Product detail ──

    @Test
    void getProduct_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/catalog/products/99999")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void getProduct_validId_returnsOk() throws Exception {
        // Product ID 1 may or may not exist — the test just verifies the endpoint is reachable
        mockMvc.perform(get("/api/catalog/products/1")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // ── Filters ──

    @Test
    void getFilters_withAuth_returnsOk() throws Exception {
        mockMvc.perform(get("/api/catalog/filters")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.categories").isArray())
                .andExpect(jsonPath("$.data.colors").isArray())
                .andExpect(jsonPath("$.data.sizes").isArray())
                .andExpect(jsonPath("$.data.stockModes").isArray());
    }

    @Test
    void getFilters_stockModes_containsAllAndInStock() throws Exception {
        mockMvc.perform(get("/api/catalog/filters")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockModes.length()").value(2));
    }

    // ── Response shape: no forbidden fields ──

    @Test
    void listProducts_responseHasNoForbiddenFields() throws Exception {
        String resp = mockMvc.perform(get("/api/catalog/products")
                .param("size", "1")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Verify forbidden fields are not in the JSON response
        assertFalse(resp.contains("\"costPrice\""), "Response must not expose costPrice");
        assertFalse(resp.contains("\"wholesalePrice\""), "Response must not expose wholesalePrice");
        assertFalse(resp.contains("\"supplierId\""), "Response must not expose supplierId");
        assertFalse(resp.contains("\"quantity\""), "Response must not expose raw quantity");
        assertFalse(resp.contains("\"reservedQty\""), "Response must not expose reservedQty");
        assertFalse(resp.contains("\"globalReservedQty\""), "Response must not expose globalReservedQty");
        assertFalse(resp.contains("\"tenantId\""), "Response must not expose tenantId");
    }

    // ── PageDTO alias ──

    @Test
    void pageDTO_pageAlias_mapsToCurrent() throws Exception {
        com.blade.catalog.dto.CatalogPageDTO dto = new com.blade.catalog.dto.CatalogPageDTO();
        dto.setPage(3L);
        assertEquals(3L, dto.getCurrent());
        assertEquals(2L * dto.getSize(), dto.offset());
    }

    @Test
    void pageDTO_defaultCurrent_isOne() {
        com.blade.catalog.dto.CatalogPageDTO dto = new com.blade.catalog.dto.CatalogPageDTO();
        assertEquals(1L, dto.getCurrent());
        assertEquals(0L, dto.offset());
    }
}
