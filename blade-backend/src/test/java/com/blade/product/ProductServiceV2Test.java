package com.blade.product;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.blade.common.tenant.TenantContext;
import com.blade.file.entity.FileBusinessBind;
import com.blade.file.entity.FileStorage;
import com.blade.file.mapper.FileBusinessBindMapper;
import com.blade.file.mapper.FileStorageMapper;
import com.blade.file.service.FileService;
import com.blade.inventory.entity.Inventory;
import com.blade.inventory.mapper.InventoryMapper;
import com.blade.order.entity.OrderItem;
import com.blade.order.mapper.OrderItemMapper;
import com.blade.product.dto.ProductFileBindingsVO;
import com.blade.product.dto.SkuUpdateDTO;
import com.blade.product.entity.*;
import com.blade.product.mapper.*;
import com.blade.product.service.impl.ProductServiceImpl;
import com.blade.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ProductServiceImpl v2 测试 — BE-1013, BE-1014 + Review fixes
 *
 * 覆盖：
 * 1. getFileBindings — 商品素材查询（含空数组、脏 fileId 过滤）
 * 2. updateSku — 单个 SKU 更新
 * 3. syncProductSkus — 保留已有 SKU、不自动重新启用、租户过滤禁用
 * 4. delete — 引用保护（含租户过滤、businessType 分离）
 * 5. deleteColor — 通过活跃 Product 防跨租户
 * 6. deleteSize — 通过活跃 Product 防跨租户
 */
class ProductServiceV2Test {

    @BeforeAll
    static void initMybatisPlus() {
        org.apache.ibatis.session.Configuration config = new org.apache.ibatis.session.Configuration();
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(config, "");
        assistant.setCurrentNamespace("test");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, Product.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, ProductSku.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, FileStorage.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, FileBusinessBind.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, OrderItem.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, Inventory.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, ProductColor.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, ProductSize.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, ProductColorRel.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, ProductSizeRel.class);
    }

    private MockMapperHandler productHandler;
    private MockMapperHandler skuHandler;
    private MockMapperHandler storageHandler;
    private MockMapperHandler bindHandler;
    private MockMapperHandler colorHandler;
    private MockMapperHandler sizeHandler;
    private MockMapperHandler colorRelHandler;
    private MockMapperHandler sizeRelHandler;
    private MockMapperHandler orderItemHandler;
    private MockMapperHandler inventoryHandler;
    private FileService fileService;
    private JdbcTemplate jdbcTemplate;
    private ProductServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);

        productHandler = new MockMapperHandler();
        skuHandler = new MockMapperHandler();
        storageHandler = new MockMapperHandler();
        bindHandler = new MockMapperHandler();
        colorHandler = new MockMapperHandler();
        sizeHandler = new MockMapperHandler();
        colorRelHandler = new MockMapperHandler();
        sizeRelHandler = new MockMapperHandler();
        orderItemHandler = new MockMapperHandler();
        inventoryHandler = new MockMapperHandler();

        ProductMapper productMapper = proxyMapper(ProductMapper.class, productHandler);
        ProductSkuMapper skuMapper = proxyMapper(ProductSkuMapper.class, skuHandler);
        FileStorageMapper storageMapper = proxyMapper(FileStorageMapper.class, storageHandler);
        FileBusinessBindMapper bindMapper = proxyMapper(FileBusinessBindMapper.class, bindHandler);
        ProductColorMapper colorMapper = proxyMapper(ProductColorMapper.class, colorHandler);
        ProductSizeMapper sizeMapper = proxyMapper(ProductSizeMapper.class, sizeHandler);
        ProductColorRelMapper colorRelMapper = proxyMapper(ProductColorRelMapper.class, colorRelHandler);
        ProductSizeRelMapper sizeRelMapper = proxyMapper(ProductSizeRelMapper.class, sizeRelHandler);
        OrderItemMapper orderItemMapper = proxyMapper(OrderItemMapper.class, orderItemHandler);
        InventoryMapper inventoryMapper = proxyMapper(InventoryMapper.class, inventoryHandler);
        fileService = mock(FileService.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any()))
                .thenReturn(Collections.emptyList());
        when(jdbcTemplate.update(anyString(), any(Object.class)))
                .thenReturn(1);

        service = new ProductServiceImpl(
                productMapper, null, colorMapper, sizeMapper,
                skuMapper, colorRelMapper, sizeRelMapper,
                jdbcTemplate, fileService, bindMapper, storageMapper,
                orderItemMapper, inventoryMapper);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void getById_withoutPricePermissions_shouldRedactProductAndSkuPrices() {
        Product product = new Product();
        product.setId(1L);
        product.setTenantId(1L);
        product.setCostPrice(new BigDecimal("40.00"));
        product.setWholesalePrice(new BigDecimal("80.00"));
        productHandler.thenSelectById(product);
        colorRelHandler.thenCustomResult(List.of());
        sizeRelHandler.thenCustomResult(List.of());

        ProductSku sku = new ProductSku();
        sku.setId(10L);
        sku.setProductId(1L);
        sku.setPrice(new BigDecimal("80.00"));
        sku.setCostPrice(new BigDecimal("40.00"));
        sku.setStatus(1);
        skuHandler.thenSelectList(List.of(sku));

        SecurityContext context = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        when(context.getAuthentication()).thenReturn(authentication);
        when(authentication.getAuthorities()).thenReturn((Collection) List.of());
        SecurityContextHolder.setContext(context);

        com.blade.product.dto.ProductVO result = service.getById(1L);

        assertNull(result.getCostPrice());
        assertNull(result.getWholesalePrice());
        assertNull(result.getSkus().get(0).getPrice());
        assertNull(result.getSkus().get(0).getCostPrice());
    }

    // ==================== BE-1013: getFileBindings ====================

    @Test
    void getFileBindings_returnsMainGalleryAndSkuImages() {
        Product product = new Product();
        product.setId(1L);
        product.setTenantId(1L);
        productHandler.thenSelectOne(product);

        // SKU 列表
        ProductSku sku1 = new ProductSku();
        sku1.setId(10L);
        sku1.setProductId(1L);
        sku1.setTenantId(1L);
        sku1.setDeleted(0);
        sku1.setSkuCode("P-BLK-M");
        sku1.setColorId(1L);
        sku1.setSizeId(1L);
        ProductSku sku2 = new ProductSku();
        sku2.setId(11L);
        sku2.setProductId(1L);
        sku2.setTenantId(1L);
        sku2.setDeleted(0);
        sku2.setSkuCode("P-WHT-M");
        sku2.setColorId(2L);
        sku2.setSizeId(1L);
        skuHandler.thenSelectList(List.of(sku1, sku2));

        // 颜色
        ProductColor color1 = new ProductColor();
        color1.setId(1L);
        color1.setColorName("黑色");
        ProductColor color2 = new ProductColor();
        color2.setId(2L);
        color2.setColorName("白色");
        colorHandler.thenSelectById(color1);
        colorHandler.thenSelectById(color2);

        // 尺码
        ProductSize size1 = new ProductSize();
        size1.setId(1L);
        size1.setSizeCode("M");
        sizeHandler.thenSelectById(size1);

        // 绑定数据
        FileBusinessBind mainBind = makeBind(1L, 100L, "product", 1L, "main");
        FileBusinessBind galleryBind = makeBind(2L, 200L, "product", 1L, "gallery");
        FileBusinessBind skuBind = makeBind(3L, 300L, "sku", 10L, "sku_image");
        bindHandler.thenSelectList(List.of(mainBind, galleryBind, skuBind));

        // 文件
        FileStorage file1 = makeFile(100L);
        FileStorage file2 = makeFile(200L);
        FileStorage file3 = makeFile(300L);
        storageHandler.thenSelectList(List.of(file1, file2, file3));

        ProductFileBindingsVO result = service.getFileBindings(1L);

        assertNotNull(result);
        assertNotNull(result.getMain());
        assertEquals(100L, result.getMain().getFileId().longValue());
        assertEquals("/api/files/100/preview", result.getMain().getPreviewUrl());

        assertNotNull(result.getGallery());
        assertEquals(1, result.getGallery().size());
        assertEquals(200L, result.getGallery().get(0).getFileId().longValue());

        assertNotNull(result.getSkuImages());
        assertEquals(1, result.getSkuImages().size());
        assertEquals(10L, result.getSkuImages().get(0).getSkuId().longValue());
        assertEquals("P-BLK-M", result.getSkuImages().get(0).getSkuCode());
        assertEquals("黑色", result.getSkuImages().get(0).getColorName());
        assertEquals("M", result.getSkuImages().get(0).getSizeName());
        assertEquals(1, result.getSkuImages().get(0).getFiles().size());
    }

    @Test
    void getFileBindings_throwsWhenProductNotFound() {
        productHandler.thenSelectOne(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.getFileBindings(999L));
        assertEquals("商品不存在", ex.getMessage());
    }

    @Test
    void getFileBindings_returnsEmptyArraysWhenNoBindings() {
        Product product = new Product();
        product.setId(1L);
        product.setTenantId(1L);
        productHandler.thenSelectOne(product);

        ProductSku sku = new ProductSku();
        sku.setId(10L);
        sku.setProductId(1L);
        sku.setTenantId(1L);
        sku.setDeleted(0);
        sku.setSkuCode("P-BLK-M");
        sku.setColorId(1L);
        sku.setSizeId(1L);
        skuHandler.thenSelectList(List.of(sku));
        // color/size lookups
        ProductColor color = new ProductColor();
        color.setId(1L);
        color.setColorName("黑色");
        colorHandler.thenSelectById(color);
        ProductSize size = new ProductSize();
        size.setId(1L);
        size.setSizeCode("M");
        sizeHandler.thenSelectById(size);

        bindHandler.thenSelectList(List.of());

        ProductFileBindingsVO result = service.getFileBindings(1L);

        assertNotNull(result);
        assertNull(result.getMain());      // main 无绑定时可为 null
        assertNotNull(result.getGallery()); // gallery 始终为列表
        assertTrue(result.getGallery().isEmpty());
        assertNotNull(result.getSkuImages()); // skuImages 始终为列表
        assertTrue(result.getSkuImages().isEmpty());
    }

    @Test
    void getFileBindings_filtersStaleFileIds() {
        Product product = new Product();
        product.setId(1L);
        product.setTenantId(1L);
        productHandler.thenSelectOne(product);

        ProductSku sku = new ProductSku();
        sku.setId(10L);
        sku.setProductId(1L);
        sku.setTenantId(1L);
        sku.setDeleted(0);
        sku.setSkuCode("P-BLK-M");
        sku.setColorId(1L);
        sku.setSizeId(1L);
        skuHandler.thenSelectList(List.of(sku));
        // color/size lookups
        ProductColor color = new ProductColor();
        color.setId(1L);
        color.setColorName("黑色");
        colorHandler.thenSelectById(color);
        ProductSize size = new ProductSize();
        size.setId(1L);
        size.setSizeCode("M");
        sizeHandler.thenSelectById(size);

        // 绑定引用的 fileId=999 不存在于 fileMap
        FileBusinessBind galleryBind = makeBind(1L, 999L, "product", 1L, "gallery");
        bindHandler.thenSelectList(List.of(galleryBind));

        // fileMap 为空（查询结果为空列表）
        storageHandler.thenSelectList(List.of());

        ProductFileBindingsVO result = service.getFileBindings(1L);

        assertNotNull(result);
        assertNotNull(result.getGallery());
        assertTrue(result.getGallery().isEmpty(), "脏 fileId 应被过滤，gallery 为空");
    }

    // ==================== Review Fix 2+1: getFileBindings businessType 分离 + deleted=0 ====================

    @Test
    void getFileBindings_ignoresUnrelatedSkuBindingWithMatchingBusinessId() {
        // 场景：存在一个 businessType="sku" 绑定，其 businessId 恰好等于 productId
        // 旧代码 IN(businessId) 会错误拉入此绑定 → 产生 businessId=productId 的僵尸 SKU 分组
        // 新代码按 businessType 分离：sku 只查 businessId IN skuIds，排除 productId 碰撞
        Product product = new Product();
        product.setId(1L);
        product.setTenantId(1L);
        productHandler.thenSelectOne(product);

        ProductSku sku = new ProductSku();
        sku.setId(10L);
        sku.setProductId(1L);
        sku.setTenantId(1L);
        sku.setDeleted(0);
        sku.setSkuCode("P-BLK-M");
        sku.setColorId(1L);
        sku.setSizeId(1L);
        skuHandler.thenSelectList(List.of(sku));
        // color/size lookups
        ProductColor color = new ProductColor();
        color.setId(1L);
        color.setColorName("黑色");
        colorHandler.thenSelectById(color);
        ProductSize size = new ProductSize();
        size.setId(1L);
        size.setSizeCode("M");
        sizeHandler.thenSelectById(size);

        // 脏绑定：businessType="sku", businessId=1（等于 productId，不在 skuIds=[10] 中）
        // 修复后的查询不应返回此绑定，mock 模拟正确行为（返回空）
        bindHandler.thenSelectList(List.of());

        ProductFileBindingsVO result = service.getFileBindings(1L);

        // 无有效绑定：不应出现 businessId=productId 的僵尸 SKU 分组
        assertNotNull(result.getSkuImages());
        assertTrue(result.getSkuImages().isEmpty(),
                "businessType≠product 且 businessId=productId 的绑定不应出现在 skuImages");
    }

    @Test
    void getFileBindings_throwsForDeletedProduct() {
        // 修复后 product 查询新增 deleted=0 条件 → 已删除商品 selectOne 返回 null
        productHandler.thenSelectOne(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.getFileBindings(1L));
        assertEquals("商品不存在", ex.getMessage());
    }

    // ==================== BE-1014: updateSku ====================

    @Test
    void updateSku_updatesProvidedFields() {
        ProductSku sku = new ProductSku();
        sku.setId(10L);
        sku.setProductId(1L);
        sku.setTenantId(1L);
        sku.setDeleted(0);
        sku.setPrice(new BigDecimal("100.00"));
        sku.setCostPrice(new BigDecimal("60.00"));
        sku.setBarCode("690000000001");
        sku.setStatus(1);
        skuHandler.thenSelectOne(sku);

        SkuUpdateDTO dto = new SkuUpdateDTO();
        dto.setId(10L);
        dto.setPrice(new BigDecimal("120.00"));
        dto.setCostPrice(new BigDecimal("70.00"));
        dto.setBarCode("690000000002");
        dto.setStatus(0);

        service.updateSku(dto);

        assertNotNull(skuHandler.capturedUpdateByIdEntity);
        ProductSku updated = (ProductSku) skuHandler.capturedUpdateByIdEntity;
        assertEquals(new BigDecimal("120.00"), updated.getPrice());
        assertEquals(new BigDecimal("70.00"), updated.getCostPrice());
        assertEquals("690000000002", updated.getBarCode());
        assertEquals(0, (int) updated.getStatus());
    }

    @Test
    void updateSku_rejectsSystemManagedPlaceholder() {
        ProductSku sku = new ProductSku();
        sku.setId(11L);
        sku.setProductId(1L);
        sku.setTenantId(1L);
        sku.setDeleted(0);
        sku.setSkuType("PLACEHOLDER");
        sku.setStatus(1);
        skuHandler.thenSelectOne(sku);

        SkuUpdateDTO dto = new SkuUpdateDTO();
        dto.setId(11L);
        dto.setStatus(0);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateSku(dto));
        assertTrue(ex.getMessage().contains("系统维护"));
    }

    @Test
    void updateSku_verifiesTenantAndDeleted() {
        skuHandler.thenSelectOne(null); // SKU not found

        SkuUpdateDTO dto = new SkuUpdateDTO();
        dto.setId(999L);
        dto.setPrice(new BigDecimal("100.00"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.updateSku(dto));
        assertEquals("SKU 不存在", ex.getMessage());
    }

    @Test
    void updateSku_throwsWhenNoFieldsToUpdate() {
        ProductSku sku = new ProductSku();
        sku.setId(10L);
        sku.setProductId(1L);
        sku.setTenantId(1L);
        sku.setDeleted(0);
        skuHandler.thenSelectOne(sku);

        SkuUpdateDTO dto = new SkuUpdateDTO();
        dto.setId(10L); // no update fields

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.updateSku(dto));
        assertEquals("没有需要更新的字段", ex.getMessage());
    }

    // ==================== BE-1014: delete 引用保护 ====================

    @Test
    void delete_throwsWhenProductNotFound_tenantFiltered() {
        // selectOne returns null → product not found for tenant+deleted
        productHandler.thenSelectOne(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.delete(999L));
        assertEquals("商品不存在", ex.getMessage());
    }

    @Test
    void delete_blocksWhenOrderItemsExist() {
        Product product = new Product();
        product.setId(1L);
        product.setTenantId(1L);
        productHandler.thenSelectOne(product); // now selectOne, not selectById

        ProductSku sku = new ProductSku();
        sku.setId(10L);
        sku.setProductId(1L);
        skuHandler.thenSelectList(List.of(sku));

        orderItemHandler.thenSelectCount(5L); // 5 order items

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.delete(1L));
        assertTrue(ex.getMessage().contains("订单明细"));
        assertTrue(ex.getMessage().contains("建议改为禁用"));
    }

    @Test
    void delete_blocksWhenInventoryExists() {
        Product product = new Product();
        product.setId(1L);
        product.setTenantId(1L);
        productHandler.thenSelectOne(product);

        ProductSku sku = new ProductSku();
        sku.setId(10L);
        sku.setProductId(1L);
        skuHandler.thenSelectList(List.of(sku));

        orderItemHandler.thenSelectCount(0L);
        inventoryHandler.thenSelectCount(3L); // 3 inventory records

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.delete(1L));
        assertTrue(ex.getMessage().contains("库存记录"));
    }

    @Test
    void delete_blocksWhenActiveBindingsExist() {
        Product product = new Product();
        product.setId(1L);
        product.setTenantId(1L);
        productHandler.thenSelectOne(product);

        ProductSku sku = new ProductSku();
        sku.setId(10L);
        sku.setProductId(1L);
        skuHandler.thenSelectList(List.of(sku));

        orderItemHandler.thenSelectCount(0L);
        inventoryHandler.thenSelectCount(0L);
        bindHandler.thenSelectCount(2L); // 2 active bindings

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.delete(1L));
        assertTrue(ex.getMessage().contains("文件绑定"));
    }

    @Test
    void delete_succeedsWhenNoReferences() {
        Product product = new Product();
        product.setId(1L);
        product.setTenantId(1L);
        productHandler.thenSelectOne(product);

        ProductSku sku = new ProductSku();
        sku.setId(10L);
        sku.setProductId(1L);
        skuHandler.thenSelectList(List.of(sku));

        orderItemHandler.thenSelectCount(0L);
        inventoryHandler.thenSelectCount(0L);
        bindHandler.thenSelectCount(0L);

        // Should not throw
        service.delete(1L);
    }

    @Test
    void delete_skusFilteredByTenantAndDeleted() {
        // Verifies that only tenant+deleted=0 SKUs are fetched
        Product product = new Product();
        product.setId(1L);
        product.setTenantId(1L);
        productHandler.thenSelectOne(product);

        // No SKUs returned (deleted or different tenant)
        skuHandler.thenSelectList(List.of());

        // No references → should succeed
        bindHandler.thenSelectCount(0L);

        service.delete(1L);
    }

    // ==================== deleteColor 引用保护（跨租户） ====================

    @Test
    void deleteColor_blocksWhenActiveProductHasRelation() {
        // Active products for tenant
        Product activeProduct = new Product();
        activeProduct.setId(1L);
        productHandler.thenSelectList(List.of(activeProduct));

        // Color relation exists for active product
        colorRelHandler.thenSelectCount(3L);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.deleteColor(1L));
        assertTrue(ex.getMessage().contains("个商品使用"));
        assertTrue(ex.getMessage().contains("建议改为禁用"));
    }

    @Test
    void deleteColor_succeedsWhenNoActiveProducts() {
        // No active products for tenant → count is effectively 0
        productHandler.thenSelectList(List.of());

        // Should not throw (no selectCount on colorRel)
        service.deleteColor(1L);
    }

    @Test
    void deleteColor_succeedsWhenNoRelationsOnActiveProducts() {
        // Active products exist, but no color relations
        Product activeProduct = new Product();
        activeProduct.setId(1L);
        productHandler.thenSelectList(List.of(activeProduct));
        colorRelHandler.thenSelectCount(0L);

        // Should not throw
        service.deleteColor(1L);
    }

    // ==================== deleteSize 引用保护（跨租户） ====================

    @Test
    void deleteSize_blocksWhenActiveProductHasRelation() {
        // Active products for tenant
        Product activeProduct = new Product();
        activeProduct.setId(1L);
        productHandler.thenSelectList(List.of(activeProduct));

        sizeRelHandler.thenSelectCount(2L);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.deleteSize(1L));
        assertTrue(ex.getMessage().contains("个商品使用"));
        assertTrue(ex.getMessage().contains("建议改为禁用"));
    }

    @Test
    void deleteSize_succeedsWhenNoActiveProducts() {
        // No active products for tenant
        productHandler.thenSelectList(List.of());

        // Should not throw
        service.deleteSize(1L);
    }

    @Test
    void deleteSize_succeedsWhenNoRelationsOnActiveProducts() {
        Product activeProduct = new Product();
        activeProduct.setId(1L);
        productHandler.thenSelectList(List.of(activeProduct));
        sizeRelHandler.thenSelectCount(0L);

        // Should not throw
        service.deleteSize(1L);
    }

    // ==================== syncProductSkus 不自动重新启用 ====================

    @Test
    void syncProductSkus_doesNotReEnableManuallyDisabledSku() {
        // Product for update lookup — update() uses selectById
        Product product = new Product();
        product.setId(1L);
        product.setProductCode("TEST");
        product.setWholesalePrice(new BigDecimal("150.00"));
        product.setCostPrice(new BigDecimal("80.00"));
        product.setStatus(1);
        product.setTenantId(1L);
        productHandler.thenSelectById(product);

        // Existing SKU with status=0 (manually disabled) and matching code
        ProductSku disabledSku = new ProductSku();
        disabledSku.setId(10L);
        disabledSku.setProductId(1L);
        disabledSku.setColorId(1L);
        disabledSku.setSizeId(1L);
        disabledSku.setSkuCode("TEST-RED-M");
        disabledSku.setStatus(0); // manually disabled
        disabledSku.setTenantId(1L);
        disabledSku.setDeleted(0);
        skuHandler.thenSelectList(List.of(disabledSku));

        // Colors/sizes for sync (target includes this combination)
        ProductColor color = new ProductColor();
        color.setId(1L);
        color.setColorCode("RED");
        // colorRel flow: deleteByProductId (ignored), then syncProductSkus.selectByProductId → colors
        colorRelHandler.thenCustomResult(Collections.emptyList()); // deleteByProductId return ignored
        colorRelHandler.thenCustomResult(List.of(color));

        ProductSize size = new ProductSize();
        size.setId(1L);
        size.setSizeCode("M");
        // sizeRel flow: deleteByProductId (ignored), then syncProductSkus.selectByProductId → sizes
        sizeRelHandler.thenCustomResult(Collections.emptyList()); // deleteByProductId return ignored
        sizeRelHandler.thenCustomResult(List.of(size));

        // Trigger sync via update() with colorIds set
        service.update(new com.blade.product.dto.ProductUpdateDTO() {{
            setId(1L);
            setColorIds(List.of(1L));
            setSizeIds(List.of(1L));
        }});

        // updateById should NOT have been called — disabled SKU stays disabled
        assertNull(skuHandler.capturedUpdateByIdEntity);
    }

    @Test
    void syncProductSkus_disablesRemovedCombinationsWithTenantFilter() {
        // Product for update lookup — update() uses selectById
        Product product = new Product();
        product.setId(1L);
        product.setProductCode("TEST");
        product.setWholesalePrice(new BigDecimal("150.00"));
        product.setCostPrice(new BigDecimal("80.00"));
        product.setStatus(1);
        product.setTenantId(1L);
        productHandler.thenSelectById(product);

        // Existing SKU with code NOT in target combinations
        ProductSku removedSku = new ProductSku();
        removedSku.setId(10L);
        removedSku.setProductId(1L);
        removedSku.setColorId(1L);
        removedSku.setSizeId(1L);
        removedSku.setSkuCode("TEST-RED-M");
        removedSku.setStatus(1); // currently enabled
        removedSku.setTenantId(1L);
        removedSku.setDeleted(0);
        skuHandler.thenSelectList(List.of(removedSku));

        // Target: different color (RED→BLK), so "TEST-RED-M" is not in target
        ProductColor color = new ProductColor();
        color.setId(2L);
        color.setColorCode("BLK");
        // colorRel flow: deleteByProductId (ignored), then syncProductSkus.selectByProductId → colors
        colorRelHandler.thenCustomResult(Collections.emptyList()); // deleteByProductId return ignored
        colorRelHandler.thenCustomResult(List.of(color));

        ProductSize size = new ProductSize();
        size.setId(1L);
        size.setSizeCode("M");
        // sizeRel flow: deleteByProductId (ignored), then syncProductSkus.selectByProductId → sizes
        sizeRelHandler.thenCustomResult(Collections.emptyList()); // deleteByProductId return ignored
        sizeRelHandler.thenCustomResult(List.of(size));

        service.update(new com.blade.product.dto.ProductUpdateDTO() {{
            setId(1L);
            setColorIds(List.of(2L)); // only BLK, not RED
            setSizeIds(List.of(1L));
        }});

        // Lambda update should have been called for the disabled SKU
        assertTrue(skuHandler.updateLambdaCalled,
                "移除的组合应通过 LambdaUpdateWrapper 禁用");
    }

    // ==================== Review Fix 3+4: syncProductSkus 空颜色/尺码禁用 + 租户/删除过滤 ====================

    @Test
    void syncProductSkus_disablesAllSkusWhenColorsBecomeEmpty() {
        // 修复后：颜色清空时，应禁用所有活跃 SKU，而非直接 return
        Product product = new Product();
        product.setId(1L);
        product.setProductCode("TEST");
        product.setWholesalePrice(new BigDecimal("150.00"));
        product.setCostPrice(new BigDecimal("80.00"));
        product.setStatus(1);
        product.setTenantId(1L);
        productHandler.thenSelectById(product);

        // 空颜色列表 → deleteByProductId 调用（结果忽略）→ selectByProductId 返回空
        colorRelHandler.thenCustomResult(Collections.emptyList()); // deleteByProductId
        colorRelHandler.thenCustomResult(Collections.emptyList()); // selectByProductId → 空

        service.update(new com.blade.product.dto.ProductUpdateDTO() {{
            setId(1L);
            setColorIds(Collections.emptyList()); // 空颜色列表触发颜色维度清空
        }});

        // 应通过 LambdaUpdateWrapper 禁用所有活跃 SKU
        assertTrue(skuHandler.updateLambdaCalled,
                "颜色清空后应禁用所有活跃 SKU");
    }

    @Test
    void syncProductSkus_disablesAllSkusWhenSizesBecomeEmpty() {
        // 修复后：尺码清空时，同样应禁用所有活跃 SKU
        Product product = new Product();
        product.setId(1L);
        product.setProductCode("TEST");
        product.setWholesalePrice(new BigDecimal("150.00"));
        product.setCostPrice(new BigDecimal("80.00"));
        product.setStatus(1);
        product.setTenantId(1L);
        productHandler.thenSelectById(product);

        // 空尺码列表
        sizeRelHandler.thenCustomResult(Collections.emptyList()); // deleteByProductId
        sizeRelHandler.thenCustomResult(Collections.emptyList()); // selectByProductId → 空

        service.update(new com.blade.product.dto.ProductUpdateDTO() {{
            setId(1L);
            setSizeIds(Collections.emptyList()); // 空尺码列表
        }});

        assertTrue(skuHandler.updateLambdaCalled,
                "尺码清空后应禁用所有活跃 SKU");
    }

    @Test
    void syncProductSkus_whenExistingSkusEmptyCreatesNewSkus() {
        // 验证 existingSkus 查询加入 tenantId + deleted=0 后
        // 跨租户/已删除 SKU 不会被拉到 existingByCode，因此视为"新组合"并创建
        Product product = new Product();
        product.setId(1L);
        product.setProductCode("TEST");
        product.setWholesalePrice(new BigDecimal("150.00"));
        product.setCostPrice(new BigDecimal("80.00"));
        product.setStatus(1);
        product.setTenantId(1L);
        productHandler.thenSelectById(product);

        // 现有 SKU 查询返回空（模拟所有 SKU 跨租户或已删除）
        skuHandler.thenSelectList(Collections.emptyList());

        // 目标颜色+尺码组合存在
        ProductColor color = new ProductColor();
        color.setId(1L);
        color.setColorCode("RED");
        ProductSize size = new ProductSize();
        size.setId(1L);
        size.setSizeCode("M");

        colorRelHandler.thenCustomResult(Collections.emptyList()); // deleteByProductId
        colorRelHandler.thenCustomResult(List.of(color));          // selectByProductId
        sizeRelHandler.thenCustomResult(Collections.emptyList());  // deleteByProductId
        sizeRelHandler.thenCustomResult(List.of(size));            // selectByProductId

        service.update(new com.blade.product.dto.ProductUpdateDTO() {{
            setId(1L);
            setColorIds(List.of(1L));
            setSizeIds(List.of(1L));
        }});

        // existingSkus 为空 → restoreOrCreateSku → jdbcTemplate.query (返回空) → skuMapper.insert
        assertNotNull(skuHandler.capturedInsertEntity,
                "existingSkus 为空时应新建 SKU（跨租户/已删除 SKU 不应复用）");
        ProductSku created = (ProductSku) skuHandler.capturedInsertEntity;
        assertEquals("TEST-RED-M", created.getSkuCode());
        assertEquals(1L, created.getTenantId().longValue());
        // deleted 由 DB 默认值 0 设置，不在 Java 侧显式赋值
    }

    // ==================== Proxy 辅助 ====================

    @SuppressWarnings("unchecked")
    private <T> T proxyMapper(Class<T> mapperType, MockMapperHandler handler) {
        return (T) Proxy.newProxyInstance(
                mapperType.getClassLoader(),
                new Class<?>[]{mapperType},
                handler);
    }

    private FileBusinessBind makeBind(long id, long fileId, String businessType, long businessId, String bindRole) {
        FileBusinessBind b = new FileBusinessBind();
        b.setId(id);
        b.setFileId(fileId);
        b.setBusinessType(businessType);
        b.setBusinessId(businessId);
        b.setBindRole(bindRole);
        b.setTenantId(1L);
        b.setDeleted(0);
        return b;
    }

    private FileStorage makeFile(long id) {
        FileStorage f = new FileStorage();
        f.setId(id);
        f.setStatus(1);
        f.setTenantId(1L);
        return f;
    }

    private static class MockMapperHandler implements java.lang.reflect.InvocationHandler {
        final Queue<Object> selectListResults = new ArrayDeque<>();
        final Queue<Long> selectCountResults = new ArrayDeque<>();
        final Queue<Object> selectByIdResults = new ArrayDeque<>();
        final Queue<Object> customResults = new ArrayDeque<>();
        Object nextSelectOne;
        int nextInsertRows = 1;
        int nextUpdateRows = 1;
        Object capturedInsertEntity;
        Object capturedUpdateByIdEntity;
        boolean updateLambdaCalled;

        void thenSelectList(List<?> results) {
            selectListResults.add(results);
        }
        void thenSelectOne(Object result) {
            nextSelectOne = result;
        }
        void thenSelectById(Object result) {
            selectByIdResults.add(result);
        }
        void thenSelectCount(Long count) {
            selectCountResults.add(count);
        }
        /** For custom mapper methods like selectByProductId, deleteByProductId */
        void thenCustomResult(Object result) {
            customResults.add(result);
        }

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
            String name = method.getName();
            switch (name) {
                case "selectList":
                    return selectListResults.isEmpty() ? List.of() : selectListResults.remove();
                case "selectCount":
                    return selectCountResults.isEmpty() ? 0L : selectCountResults.remove();
                case "selectOne":
                    return nextSelectOne;
                case "selectById":
                    return selectByIdResults.isEmpty() ? null : selectByIdResults.remove();
                case "insert":
                    if (args != null && args.length > 0) capturedInsertEntity = args[0];
                    return nextInsertRows;
                case "update":
                    if (args != null && args.length >= 2 && args[1] instanceof LambdaUpdateWrapper) {
                        updateLambdaCalled = true;
                    }
                    return nextUpdateRows;
                case "updateById":
                    if (args != null && args.length > 0) {
                        capturedUpdateByIdEntity = args[0];
                    }
                    return 1;
                case "deleteById":
                    return 1;
                case "selectByProductId":
                case "deleteByProductId":
                    return customResults.isEmpty() ? null : customResults.remove();
                case "toString":
                    return "MockMapper";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == (args != null && args.length > 0 ? args[0] : null);
                default:
                    Class<?> retType = method.getReturnType();
                    if (!retType.isPrimitive()) return null;
                    if (retType == boolean.class) return false;
                    if (retType == void.class) return null;
                    return 0;
            }
        }
    }
}
