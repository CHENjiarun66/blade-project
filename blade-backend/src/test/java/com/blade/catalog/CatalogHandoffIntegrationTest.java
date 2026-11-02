package com.blade.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mock.web.MockMultipartFile;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real JWT, MySQL and existing draft writer; every fixture is a new tenant and rolls back. */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class CatalogHandoffIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired org.mybatis.spring.SqlSessionTemplate sqlSession;
    String token; Long tenantId,userId,outletId,productId; String code;
    @BeforeEach void fixture() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-","").substring(0,10), tenant = "cat_"+suffix, user = "cat_"+suffix;
        jdbc.update("INSERT INTO sys_tenant(tenant_name,tenant_code,status) VALUES(?,?,1)","Catalog test",tenant);
        tenantId = jdbc.queryForObject("SELECT id FROM sys_tenant WHERE tenant_code=?",Long.class,tenant);
        jdbc.update("INSERT INTO sys_user(username,password,nickname,status,tenant_id) VALUES(?,?,?,1,?)",user,"$2a$10$KLMg4aoraH703rwMILz3IOVcD.OgKEBAglSrZhOUTxhzBAT7gBRJq","Catalog test",tenantId);
        userId = jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,user);
        jdbc.update("INSERT INTO sys_role(role_name,role_code,tenant_id,status) VALUES('Catalog owner','ROLE_OWNER',?,1)",tenantId);
        Long role = jdbc.queryForObject("SELECT id FROM sys_role WHERE tenant_id=? AND role_code='ROLE_OWNER'",Long.class,tenantId);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id,tenant_id) VALUES(?,?,?)",userId,role,tenantId);
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id,tenant_id) SELECT ?,id,? FROM sys_permission WHERE deleted=0",role,tenantId);
        jdbc.update("INSERT INTO sales_outlet(tenant_id,outlet_code,outlet_name,is_tenant_default,status) VALUES(?,'CATALOG','Catalog test',1,1)",tenantId);
        outletId = jdbc.queryForObject("SELECT id FROM sales_outlet WHERE tenant_id=?",Long.class,tenantId);
        code = "CAT-"+suffix;
        jdbc.update("INSERT INTO product(tenant_id,product_code,name,status) VALUES(?,?,?,1)",tenantId,code,"Catalog test garment");
        productId = jdbc.queryForObject("SELECT id FROM product WHERE tenant_id=? AND product_code=?",Long.class,tenantId,code);
        JsonNode login = json.readTree(mvc.perform(post("/api/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("tenantCode",tenant,"username",user,"password","admin123")))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        token = login.path("accessToken").asText(login.path("token").asText()); assertFalse(token.isBlank(),login.toString());
    }
    Map<String,Object> body(String requestId) {
        return new LinkedHashMap<>(Map.of("requestId",requestId,"sourceOutletId",outletId,"items",List.of(new LinkedHashMap<>(Map.of("productId",productId,"colorNote","混色（3色）","quantity",120,"unitPrice","28.00","amount",1)))));
    }
    JsonNode submit(Map<String,Object> body) throws Exception {
        return json.readTree(mvc.perform(post("/api/catalog/handoffs").header("Authorization","Bearer "+token).contentType("application/json").content(json.writeValueAsString(body))).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
    @Test void missingOrInvalidLoginIs401RatherThanPermission403() throws Exception {
        mvc.perform(get("/api/catalog/products")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/catalog/handoffs").header("Authorization","Bearer invalid-token")
            .contentType("application/json").content("{}"))
            .andExpect(status().isUnauthorized());
    }
    @Test void realHandoffPreservesWholesaleFieldsAndRecomputesAmountWithoutOrder() throws Exception {
        JsonNode response = submit(body(UUID.randomUUID().toString())); assertEquals(200,response.path("code").asInt(),response.toString());
        long draftId = response.path("data").path("draftId").asLong(); assertTrue(draftId>0);
        JsonNode draft = json.readTree(mvc.perform(get("/api/order-drafts/"+draftId).header("Authorization","Bearer "+token)).andExpect(jsonPath("$.code").value(200)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        JsonNode row = draft.path("items").get(0);
        assertEquals("混色（3色）",row.path("rawColor").asText()); assertEquals(120,row.path("quantity").asInt());
        assertEquals(0,new java.math.BigDecimal("3360.00").compareTo(row.path("paperAmount").decimalValue())); assertTrue(row.path("skuId").isNull());
        assertEquals(userId,jdbc.queryForObject("SELECT created_by_user_id FROM order_draft WHERE id=?",Long.class,draftId));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sale_order WHERE tenant_id=?",Integer.class,tenantId));
    }
    @Test void retryReturnsSameDraftAndDoesNotOverwriteStaffPrice() throws Exception {
        Map<String,Object> body = body(UUID.randomUUID().toString()); JsonNode first = submit(body); assertEquals(200,first.path("code").asInt(),first.toString());
        long id = first.path("data").path("draftId").asLong(); jdbc.update("UPDATE order_draft_item SET sale_price=31.50 WHERE draft_id=? AND tenant_id=?",id,tenantId);
        JsonNode retry = submit(body); assertEquals(id,retry.path("data").path("draftId").asLong()); assertTrue(retry.path("data").path("alreadyExists").asBoolean());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM order_draft WHERE tenant_id=?",Integer.class,tenantId));
        assertEquals(0,new java.math.BigDecimal("31.50").compareTo(jdbc.queryForObject("SELECT sale_price FROM order_draft_item WHERE draft_id=?",java.math.BigDecimal.class,id)));
    }
    @Test void foreignTenantStyleCannotCreateADraft() throws Exception {
        jdbc.update("UPDATE product SET tenant_id=? WHERE id=?",tenantId+100000,productId);
        JsonNode result = submit(body(UUID.randomUUID().toString())); assertEquals(400,result.path("code").asInt(),result.toString());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM order_draft WHERE tenant_id=?",Integer.class,tenantId));
    }
    @Test void exactLookupAndSelectedHydrationStayTenantScoped() throws Exception {
        mvc.perform(get("/api/catalog/products").param("productCode",code).header("Authorization","Bearer "+token)).andExpect(jsonPath("$.data.records[0].id").value(productId));
        mvc.perform(get("/api/catalog/products").param("productCode",code+"-missing").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/catalog/products").param("productIds",String.valueOf(productId)).header("Authorization","Bearer "+token)).andExpect(jsonPath("$.data.records[0].id").value(productId));
    }
    @Test void imageFilterIncludesGalleryAndSkuOnlyPhotosButNotInactiveFiles() throws Exception {
        byte[] pixel = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        JsonNode upload = json.readTree(mvc.perform(multipart("/api/files/upload")
            .file(new MockMultipartFile("file","catalog-test.png","image/png",pixel))
            .param("businessType","product").param("businessId",String.valueOf(productId)).header("Authorization","Bearer "+token))
            .andExpect(jsonPath("$.code").value(200)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        long file = upload.path("data").path("id").asLong();
        mvc.perform(put("/api/products/"+productId+"/file-bindings").header("Authorization","Bearer "+token).contentType("application/json")
            .content(json.writeValueAsString(Map.of("galleryFileIds",List.of(file))))).andExpect(jsonPath("$.code").value(200));
        sqlSession.clearCache(); // JDBC fixtures bypass MyBatis; emulate the next HTTP request session.
        mvc.perform(get("/api/catalog/products").param("hasImage","true").header("Authorization","Bearer "+token))
            .andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.records[0].hasImage").value(true));
        jdbc.update("UPDATE file_business_bind SET deleted=1 WHERE tenant_id=? AND business_type='product' AND business_id=?",tenantId,productId);
        jdbc.update("INSERT INTO product_color(tenant_id,color_code,color_name) VALUES(?,'CAT','Catalog color')",tenantId);
        jdbc.update("INSERT INTO product_size(tenant_id,size_code) VALUES(?,'CAT')",tenantId);
        Long colorId=jdbc.queryForObject("SELECT id FROM product_color WHERE tenant_id=?",Long.class,tenantId);
        Long sizeId=jdbc.queryForObject("SELECT id FROM product_size WHERE tenant_id=?",Long.class,tenantId);
        jdbc.update("INSERT INTO product_sku(tenant_id,product_id,color_id,size_id,sku_code,sku_type,price,status) VALUES(?,?,?,?,?,'NORMAL',0,1)",tenantId,productId,colorId,sizeId,code+"-SKU");
        Long skuId=jdbc.queryForObject("SELECT id FROM product_sku WHERE tenant_id=? AND product_id=?",Long.class,tenantId,productId);
        jdbc.update("INSERT INTO file_business_bind(tenant_id,file_id,business_type,business_id,bind_role,deleted) VALUES(?,?,'sku',?,'gallery',0)",tenantId,file,skuId);
        sqlSession.clearCache(); // JDBC fixtures bypass MyBatis; emulate the next HTTP request session.
        mvc.perform(get("/api/catalog/products").param("hasImage","true").header("Authorization","Bearer "+token))
            .andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.records[0].hasImage").value(true));
        jdbc.update("UPDATE file_storage SET status=0 WHERE id=? AND tenant_id=?",file,tenantId);
        sqlSession.clearCache(); // JDBC fixtures bypass MyBatis; emulate the next HTTP request session.
        mvc.perform(get("/api/catalog/products").param("hasImage","true").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.data.total").value(0));
    }

    @Test void customerAvailableCountIsAggregatedAndNeverIncludesForeignTenantStock() throws Exception {
        jdbc.update("INSERT INTO product_color(tenant_id,color_code,color_name) VALUES(?,'CAT-A','White'),(?,'CAT-B','Black')",tenantId,tenantId);
        jdbc.update("INSERT INTO product_size(tenant_id,size_code) VALUES(?,'M')",tenantId);
        Long colorA=jdbc.queryForObject("SELECT id FROM product_color WHERE tenant_id=? AND color_code='CAT-A'",Long.class,tenantId);
        Long colorB=jdbc.queryForObject("SELECT id FROM product_color WHERE tenant_id=? AND color_code='CAT-B'",Long.class,tenantId);
        Long size=jdbc.queryForObject("SELECT id FROM product_size WHERE tenant_id=?",Long.class,tenantId);
        jdbc.update("INSERT INTO product_sku(tenant_id,product_id,color_id,size_id,sku_code,sku_type,price,status) VALUES(?,?,?,?,?,'NORMAL',0,1),(?,?,?,?,?,'NORMAL',0,1)",tenantId,productId,colorA,size,code+"-A",tenantId,productId,colorB,size,code+"-B");
        Long skuA=jdbc.queryForObject("SELECT id FROM product_sku WHERE sku_code=?",Long.class,code+"-A");
        Long skuB=jdbc.queryForObject("SELECT id FROM product_sku WHERE sku_code=?",Long.class,code+"-B");
        jdbc.update("INSERT INTO warehouse(tenant_id,warehouse_name,status) VALUES(?,'Catalog A',1),(?,'Catalog B',1)",tenantId,tenantId);
        Long whA=jdbc.queryForObject("SELECT id FROM warehouse WHERE tenant_id=? AND warehouse_name='Catalog A'",Long.class,tenantId);
        Long whB=jdbc.queryForObject("SELECT id FROM warehouse WHERE tenant_id=? AND warehouse_name='Catalog B'",Long.class,tenantId);
        jdbc.update("INSERT INTO inventory(tenant_id,sku_id,warehouse_id,quantity,reserved_qty,global_reserved_qty) VALUES(?,?,?,100,20,10),(?,?,?,5,7,0),(?,?,?,30,0,0),(?,?,?,999,0,0)",tenantId,skuA,whA,tenantId,skuA,whB,tenantId,skuB,whA,tenantId+100000,skuB,whB);
        sqlSession.clearCache();
        mvc.perform(get("/api/catalog/products/"+productId).header("Authorization","Bearer "+token))
            .andExpect(jsonPath("$.data.availableQty").value(100))
            .andExpect(jsonPath("$.data.hasStock").value(true))
            .andExpect(jsonPath("$.data.quantity").doesNotExist())
            .andExpect(jsonPath("$.data.reservedQty").doesNotExist());
        mvc.perform(get("/api/catalog/products").param("productIds",String.valueOf(productId)).header("Authorization","Bearer "+token))
            .andExpect(jsonPath("$.data.records[0].availableQty").value(100));
    }
}
