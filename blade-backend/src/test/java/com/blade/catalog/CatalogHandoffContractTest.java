package com.blade.catalog;

import com.blade.catalog.controller.CatalogHandoffController;
import com.blade.catalog.dto.CatalogHandoffDTO;
import com.blade.catalog.service.CatalogHandoffService;
import com.blade.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class CatalogHandoffContractTest {
    AnnotationConfigApplicationContext context;
    CatalogHandoffController controller;
    CatalogHandoffService service;
    MockMvc mvc;
    @Configuration @EnableMethodSecurity static class Config {
        @Bean CatalogHandoffService service() { return mock(CatalogHandoffService.class); }
        @Bean CatalogHandoffController controller(CatalogHandoffService service) { return new CatalogHandoffController(service); }
    }
    @BeforeEach void setup() {
        context = new AnnotationConfigApplicationContext(Config.class); controller = context.getBean(CatalogHandoffController.class); service = context.getBean(CatalogHandoffService.class);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); context.close(); }
    void auth(String... authorities) { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("staff","unused",Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList())); }
    String body(String quantity, String price) { return "{\"requestId\":\"448e8765-d1f0-4c80-a3d0-513e844ad254\",\"sourceOutletId\":7,\"items\":[{\"productId\":11,\"quantity\":"+quantity+",\"unitPrice\":"+price+"}]}"; }
    @Test void catalogReadAloneCannotCreateDrafts() { auth("data:catalog:view"); assertThrows(AccessDeniedException.class,()->controller.handoff(new CatalogHandoffDTO.Request())); verifyNoInteractions(service); }
    @Test void orderCreateAloneCannotUseCatalogHandoff() { auth("btn:order:create"); assertThrows(AccessDeniedException.class,()->controller.handoff(new CatalogHandoffDTO.Request())); verifyNoInteractions(service); }
    @Test void authorisedStaffCanUseHandoff() { auth("data:catalog:view","btn:order:create"); controller.handoff(new CatalogHandoffDTO.Request()); verify(service).handoff(any()); }
    @Test void invalidPricesAreRejectedBeforeService() throws Exception { auth("data:catalog:view","btn:order:create"); mvc.perform(post("/api/catalog/handoffs").contentType("application/json").content(body("120","28.001"))).andExpect(jsonPath("$.code").value(400)); verifyNoInteractions(service); }
    @Test void negativeQuantityIsRejectedBeforeService() throws Exception { auth("data:catalog:view","btn:order:create"); mvc.perform(post("/api/catalog/handoffs").contentType("application/json").content(body("-1","28.00"))).andExpect(jsonPath("$.code").value(400)); verifyNoInteractions(service); }
    @Test void fractionalQuantityCannotBeSilentlyTruncated() throws Exception { auth("data:catalog:view","btn:order:create"); mvc.perform(post("/api/catalog/handoffs").contentType("application/json").content(body("1.5","28.00"))).andExpect(jsonPath("$.code").value(400)); verifyNoInteractions(service); }
    @Test void absentPriceAndQuantityRemainValidPendingFields() throws Exception { auth("data:catalog:view","btn:order:create"); mvc.perform(post("/api/catalog/handoffs").contentType("application/json").content(body("null","null"))).andExpect(jsonPath("$.code").value(200)); verify(service).handoff(any()); }
}
