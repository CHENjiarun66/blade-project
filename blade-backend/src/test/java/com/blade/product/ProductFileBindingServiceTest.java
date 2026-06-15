package com.blade.product;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.blade.common.tenant.TenantContext;
import com.blade.file.entity.FileBusinessBind;
import com.blade.file.entity.FileStorage;
import com.blade.file.mapper.FileBusinessBindMapper;
import com.blade.file.mapper.FileStorageMapper;
import com.blade.file.service.FileService;
import com.blade.product.dto.ProductFileBindingDTO;
import com.blade.product.dto.ProductUpdateDTO;
import com.blade.product.dto.SkuImageBindingDTO;
import com.blade.product.entity.Product;
import com.blade.product.entity.ProductSku;
import com.blade.product.mapper.ProductMapper;
import com.blade.product.mapper.ProductSkuMapper;
import com.blade.product.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * ProductServiceImpl.bindFiles 测试 — BE-1005 商品/SKU 图片绑定服务
 * 使用 JDK Proxy mock mapper。
 *
 * 覆盖：
 * 1. mainFileId 替换主图绑定并更新 product.imageUrl
 * 2. galleryFileIds 空列表清空图集
 * 3. skuImageBindings 校验 SKU 归属
 * 4. non-numeric imageUrl 不同步到绑定表 (syncMainImageBinding)
 */
class ProductFileBindingServiceTest {

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
    }

    private MockMapperHandler productHandler;
    private MockMapperHandler skuHandler;
    private MockMapperHandler storageHandler;
    private MockMapperHandler bindHandler;
    private FileService fileService;
    private ProductServiceImpl service;
    private final AtomicReference<Product> capturedProductUpdate = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
        capturedProductUpdate.set(null);

        productHandler = new MockMapperHandler();
        skuHandler = new MockMapperHandler();
        storageHandler = new MockMapperHandler();
        bindHandler = new MockMapperHandler();

        ProductMapper productMapper = proxyMapper(ProductMapper.class, productHandler);
        ProductSkuMapper skuMapper = proxyMapper(ProductSkuMapper.class, skuHandler);
        FileStorageMapper storageMapper = proxyMapper(FileStorageMapper.class, storageHandler);
        FileBusinessBindMapper bindMapper = proxyMapper(FileBusinessBindMapper.class, bindHandler);
        fileService = mock(FileService.class);

        service = new ProductServiceImpl(
                productMapper, null, null, null,
                skuMapper, null, null,
                null, fileService, bindMapper, storageMapper,
                null, null);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ==================== 测试 1: mainFileId 替换主图绑定并更新 imageUrl ====================

    @Test
    void bindFiles_mainFileId_replacesMainBindingAndUpdatesImageUrl() {
        // 商品存在
        Product product = new Product();
        product.setId(1L);
        product.setTenantId(1L);
        product.setStatus(1);
        productHandler.thenSelectOne(product);
        // 文件存在
        storageHandler.thenSelectCount(1L);

        ProductFileBindingDTO dto = new ProductFileBindingDTO();
        dto.setMainFileId(100L);

        service.bindFiles(1L, dto);

        // 验证插入了绑定记录
        assertNotNull(bindHandler.capturedInsertEntity);
        FileBusinessBind bind = (FileBusinessBind) bindHandler.capturedInsertEntity;
        assertEquals(100L, bind.getFileId().longValue());
        assertEquals("product", bind.getBusinessType());
        assertEquals("main", bind.getBindRole());
        assertEquals(1, bind.getIsPrimary().intValue());
        assertNotNull(productHandler.capturedUpdateByIdEntity);
        assertEquals("100", ((Product) productHandler.capturedUpdateByIdEntity).getImageUrl());
    }

    // ==================== 测试 2: galleryFileIds 空列表清空图集 ====================

    @Test
    void bindFiles_emptyGallery_clearsGalleryWithoutInsertingNewRows() {
        Product product = new Product();
        product.setId(1L);
        product.setTenantId(1L);
        productHandler.thenSelectOne(product);

        ProductFileBindingDTO dto = new ProductFileBindingDTO();
        dto.setGalleryFileIds(List.of()); // 空列表 = 清空

        service.bindFiles(1L, dto);

        // 验证调用了 softDelete
        assertTrue(bindHandler.updateLambdaCalled);
        // 验证没有插入新绑定
        assertNull(bindHandler.capturedInsertEntity);
    }

    @Test
    void bindFiles_galleryFileIds_populatesGalleryBindings() {
        Product product = new Product();
        product.setId(1L);
        product.setTenantId(1L);
        productHandler.thenSelectOne(product);
        storageHandler.thenSelectCount(2L);

        ProductFileBindingDTO dto = new ProductFileBindingDTO();
        dto.setGalleryFileIds(List.of(200L, 201L));

        service.bindFiles(1L, dto);

        assertNotNull(bindHandler.capturedInsertEntity);
    }

    @Test
    void bindFiles_duplicateFileIds_validateOnce() {
        Product product = new Product();
        product.setId(1L);
        product.setTenantId(1L);
        productHandler.thenSelectOne(product);
        storageHandler.thenSelectCount(1L);

        ProductFileBindingDTO dto = new ProductFileBindingDTO();
        dto.setMainFileId(100L);
        dto.setGalleryFileIds(List.of(100L));

        service.bindFiles(1L, dto);

        assertNotNull(bindHandler.capturedInsertEntity);
    }

    // ==================== 测试 3: skuImageBindings 验证 SKU 归属 ====================

    @Test
    void bindFiles_skuBinding_throwsWhenSkuNotBelongToProduct() {
        Product product = new Product();
        product.setId(1L);
        product.setTenantId(1L);
        productHandler.thenSelectOne(product);
        storageHandler.thenSelectCount(1L); // 文件存在
        skuHandler.thenSelectOne(null); // SKU 不存在

        ProductFileBindingDTO dto = new ProductFileBindingDTO();
        SkuImageBindingDTO skuBinding = new SkuImageBindingDTO();
        skuBinding.setSkuId(999L);
        skuBinding.setFileIds(List.of(300L));
        dto.setSkuImageBindings(List.of(skuBinding));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.bindFiles(1L, dto));
        assertTrue(ex.getMessage().contains("SKU 不存在"));
    }

    @Test
    void bindFiles_skuBinding_validSku_bindsImages() {
        Product product = new Product();
        product.setId(1L);
        product.setTenantId(1L);
        productHandler.thenSelectOne(product);
        ProductSku sku = new ProductSku();
        sku.setId(10L);
        sku.setProductId(1L);
        sku.setTenantId(1L);
        sku.setStatus(1);
        sku.setDeleted(0);
        skuHandler.thenSelectOne(sku);
        storageHandler.thenSelectCount(2L);

        ProductFileBindingDTO dto = new ProductFileBindingDTO();
        SkuImageBindingDTO skuBinding = new SkuImageBindingDTO();
        skuBinding.setSkuId(10L);
        skuBinding.setFileIds(List.of(301L, 302L));
        dto.setSkuImageBindings(List.of(skuBinding));

        service.bindFiles(1L, dto);

        assertNotNull(bindHandler.capturedInsertEntity);
        FileBusinessBind bind = (FileBusinessBind) bindHandler.capturedInsertEntity;
        assertEquals("sku", bind.getBusinessType());
        assertEquals(10L, bind.getBusinessId().longValue());
        assertEquals("sku_image", bind.getBindRole());
    }

    @Test
    void bindFiles_skuBinding_emptyFileIds_clearsSkuImages() {
        Product product = new Product();
        product.setId(1L);
        product.setTenantId(1L);
        productHandler.thenSelectOne(product);
        ProductSku sku = new ProductSku();
        sku.setId(10L);
        sku.setProductId(1L);
        sku.setTenantId(1L);
        sku.setStatus(1);
        sku.setDeleted(0);
        skuHandler.thenSelectOne(sku);

        ProductFileBindingDTO dto = new ProductFileBindingDTO();
        SkuImageBindingDTO skuBinding = new SkuImageBindingDTO();
        skuBinding.setSkuId(10L);
        skuBinding.setFileIds(List.of()); // 空 = 清空
        dto.setSkuImageBindings(List.of(skuBinding));

        service.bindFiles(1L, dto);

        // 验证调用了 softDelete（通过 update lambda）
        assertTrue(bindHandler.updateLambdaCalled);
    }

    // ==================== 测试 4: 边界场景 ====================

    @Test
    void bindFiles_throwsWhenProductNotFound() {
        productHandler.thenSelectOne(null);

        ProductFileBindingDTO dto = new ProductFileBindingDTO();
        dto.setMainFileId(100L);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.bindFiles(999L, dto));
        assertEquals("商品不存在", ex.getMessage());
    }

    @Test
    void bindFiles_throwsWhenFileNotFound() {
        Product product = new Product();
        product.setId(1L);
        product.setTenantId(1L);
        productHandler.thenSelectOne(product);
        storageHandler.thenSelectCount(0L); // 不存在

        ProductFileBindingDTO dto = new ProductFileBindingDTO();
        dto.setMainFileId(999L);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.bindFiles(1L, dto));
        assertEquals("部分文件不存在", ex.getMessage());
    }

    @Test
    void bindFiles_allThreeRolesTogether() {
        Product product = new Product();
        product.setId(1L);
        product.setTenantId(1L);
        productHandler.thenSelectOne(product);
        storageHandler.thenSelectCount(5L); // 1 main + 2 gallery + 2 sku
        ProductSku sku = new ProductSku();
        sku.setId(10L);
        sku.setProductId(1L);
        sku.setTenantId(1L);
        sku.setStatus(1);
        sku.setDeleted(0);
        skuHandler.thenSelectOne(sku);

        ProductFileBindingDTO dto = new ProductFileBindingDTO();
        dto.setMainFileId(100L);
        dto.setGalleryFileIds(List.of(200L, 201L));
        SkuImageBindingDTO skuBinding = new SkuImageBindingDTO();
        skuBinding.setSkuId(10L);
        skuBinding.setFileIds(List.of(301L, 302L));
        dto.setSkuImageBindings(List.of(skuBinding));

        // Should not throw
        service.bindFiles(1L, dto);
    }

    @Test
    void update_legacyImageUrl_doesNotCreateBinding() {
        Product product = new Product();
        product.setId(1L);
        product.setTenantId(1L);
        productHandler.thenSelectOne(product);

        ProductUpdateDTO dto = new ProductUpdateDTO();
        dto.setId(1L);
        dto.setImageUrl("https://legacy.example.com/a.jpg");

        service.update(dto);

        assertNull(bindHandler.capturedInsertEntity);
    }

    // ==================== Proxy 辅助 ====================

    @SuppressWarnings("unchecked")
    private <T> T proxyMapper(Class<T> mapperType, MockMapperHandler handler) {
        return (T) Proxy.newProxyInstance(
                mapperType.getClassLoader(),
                new Class<?>[]{mapperType},
                handler);
    }

    private static class MockMapperHandler implements java.lang.reflect.InvocationHandler {
        final Queue<Object> selectListResults = new ArrayDeque<>();
        final Queue<Long> selectCountResults = new ArrayDeque<>();
        Object nextSelectOne;
        int nextInsertRows;
        int nextUpdateRows;
        Object capturedInsertEntity;
        Object capturedUpdateByIdEntity;
        boolean updateLambdaCalled;

        void thenSelectList(List<?>... results) {
            selectListResults.addAll(List.of(results));
        }
        void thenSelectOne(Object result) {
            nextSelectOne = result;
        }
        void thenSelectCount(Long count) {
            selectCountResults.add(count);
        }
        void thenInsert(int rows) { nextInsertRows = rows; }

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
                    return nextSelectOne;
                case "insert":
                    if (args != null && args.length > 0) capturedInsertEntity = args[0];
                    return nextInsertRows > 0 ? nextInsertRows : 1;
                case "update":
                    // LambdaUpdateWrapper usage
                    if (args != null && args.length >= 2 && args[1] instanceof LambdaUpdateWrapper) {
                        updateLambdaCalled = true;
                    }
                    return nextUpdateRows > 0 ? nextUpdateRows : (updateLambdaCalled ? 1 : 0);
                case "updateById":
                    if (args != null && args.length > 0) {
                        capturedUpdateByIdEntity = args[0];
                    }
                    return 1;
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
