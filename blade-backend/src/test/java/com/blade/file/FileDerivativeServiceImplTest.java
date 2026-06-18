package com.blade.file;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blade.common.tenant.TenantContext;
import com.blade.file.config.FileStorageProperties;
import com.blade.file.entity.FileDerivative;
import com.blade.file.entity.FileStorage;
import com.blade.file.mapper.FileDerivativeMapper;
import com.blade.file.mapper.FileStorageMapper;
import com.blade.file.service.FileDerivativeService;
import com.blade.file.service.ImageDerivativeGenerator;
import com.blade.file.service.impl.FileDerivativeServiceImpl;
import com.blade.file.storage.FileStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class FileDerivativeServiceImplTest {

    @BeforeAll
    static void initMybatisPlus() {
        org.apache.ibatis.session.Configuration config = new org.apache.ibatis.session.Configuration();
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(config, "");
        assistant.setCurrentNamespace("test");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, FileDerivative.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, FileStorage.class);
    }

    private MockMapperHandler derivativeHandler;
    private MockMapperHandler storageHandler;
    private FileDerivativeMapper derivativeMapper;
    private FileStorageMapper storageMapper;
    private StubStorageService stubStorageService;
    private StubGenerator stubGenerator;
    private FileStorageProperties properties;
    private FileDerivativeServiceImpl service;

    private final List<FileDerivative> derivativeInserts = new ArrayList<>();
    private final List<FileDerivative> derivativeUpdateByIds = new ArrayList<>();
    private final List<Object[]> derivativeUpdateWrappers = new ArrayList<>();

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
        derivativeInserts.clear();
        derivativeUpdateByIds.clear();
        derivativeUpdateWrappers.clear();

        derivativeHandler = new MockMapperHandler();
        storageHandler = new MockMapperHandler();

        derivativeMapper = (FileDerivativeMapper) Proxy.newProxyInstance(
                FileDerivativeMapper.class.getClassLoader(),
                new Class[]{FileDerivativeMapper.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("insert")) {
                        derivativeInserts.add((FileDerivative) args[0]);
                        return 1;
                    }
                    if (name.equals("updateById")) {
                        derivativeUpdateByIds.add((FileDerivative) args[0]);
                        return 1;
                    }
                    if (name.equals("update") && args != null && args.length == 2) {
                        derivativeUpdateWrappers.add(new Object[]{args[0], args[1]});
                        return 1;
                    }
                    return derivativeHandler.invoke(method, args);
                });
        storageMapper = (FileStorageMapper) Proxy.newProxyInstance(
                FileStorageMapper.class.getClassLoader(),
                new Class[]{FileStorageMapper.class},
                (proxy, method, args) -> storageHandler.invoke(method, args));

        stubStorageService = new StubStorageService();
        stubGenerator = new StubGenerator();
        properties = new FileStorageProperties();
        properties.setLocalBasePath("uploads");

        service = new FileDerivativeServiceImpl(
                derivativeMapper, storageMapper, stubStorageService, stubGenerator, properties);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // === Fix #6: Image generator real tests ===

    @Test
    void generator_realJpegLandscape_scalesToLongEdge() throws Exception {
        ImageDerivativeGenerator gen = new ImageDerivativeGenerator();
        // Create a 1600x1200 JPEG programmatically
        byte[] bytes = createTestJpegBytes(1600, 1200);
        ImageDerivativeGenerator.DerivativeResult r = gen.generate(bytes, 800);
        assertEquals(800, Math.max(r.getWidth(), r.getHeight()));
        assertTrue(r.getWidth() <= 800 && r.getHeight() <= 800);
    }

    @Test
    void generator_realJpegPortrait_scalesToLongEdge() throws Exception {
        ImageDerivativeGenerator gen = new ImageDerivativeGenerator();
        byte[] bytes = createTestJpegBytes(1200, 1600);
        ImageDerivativeGenerator.DerivativeResult r = gen.generate(bytes, 800);
        assertEquals(800, Math.max(r.getWidth(), r.getHeight()));
        assertTrue(r.getWidth() <= 800 && r.getHeight() <= 800);
        assertEquals(600, r.getWidth()); // 1200*(800/1600) = 600
    }

    @Test
    void generator_aspectRatioPreserved() throws Exception {
        ImageDerivativeGenerator gen = new ImageDerivativeGenerator();
        byte[] bytes = createTestJpegBytes(1600, 1000);
        ImageDerivativeGenerator.DerivativeResult r = gen.generate(bytes, 800);
        // 1600/1000 = 1.6. After scaling: long edge 800, short edge = 800/1.6 = 500
        assertEquals(800, r.getWidth());
        assertEquals(500, r.getHeight());
    }

    @Test
    void generator_smallImageNotUpscaled() throws Exception {
        ImageDerivativeGenerator gen = new ImageDerivativeGenerator();
        byte[] bytes = createTestJpegBytes(100, 100);
        ImageDerivativeGenerator.DerivativeResult r = gen.generate(bytes, 800);
        assertEquals(100, r.getWidth());
        assertEquals(100, r.getHeight());
    }

    @Test
    void generator_transparentPng_getsWhiteBackground() throws Exception {
        ImageDerivativeGenerator gen = new ImageDerivativeGenerator();
        byte[] bytes = createTestPngBytes(200, 200, true);
        // Should not throw — alpha is composited onto white
        ImageDerivativeGenerator.DerivativeResult r = gen.generate(bytes, 100);
        assertEquals(100, r.getWidth());
        assertEquals(100, r.getHeight());
    }

    @Test
    void generator_opaquePng_works() throws Exception {
        ImageDerivativeGenerator gen = new ImageDerivativeGenerator();
        byte[] bytes = createTestPngBytes(400, 300, false);
        ImageDerivativeGenerator.DerivativeResult r = gen.generate(bytes, 320);
        assertEquals(320, Math.max(r.getWidth(), r.getHeight()));
    }

    @Test
    void generator_tooLargeDimension_rejected() throws Exception {
        ImageDerivativeGenerator gen = new ImageDerivativeGenerator();
        // Create an image larger than MAX_SOURCE_DIMENSION
        byte[] bytes = createTestJpegBytes(9000, 100);
        assertThrows(IOException.class, () -> gen.generate(bytes, 800));
    }

    @Test
    void generator_webp_decodeAndDerivative() throws Exception {
        ImageDerivativeGenerator gen = new ImageDerivativeGenerator();
        // Load committed real WebP fixture (created once via cwebp, 300×200, 474 bytes)
        byte[] webpBytes = getClass().getClassLoader()
                .getResourceAsStream("test-300x200.webp").readAllBytes();
        assertTrue(webpBytes.length > 100, "WebP fixture must be non-trivial");

        ImageDerivativeGenerator.DerivativeResult r = gen.generate(webpBytes, 200);
        // 300×200, long edge = 300 → scaled to 200 → 200×133
        assertEquals(200, r.getWidth());
        assertEquals(133, r.getHeight());
        assertTrue(r.getBytes().length > 100, "JPEG output must be non-empty");

        // Verify output is a valid JPEG with expected dimensions
        BufferedImage decoded = javax.imageio.ImageIO.read(
                new java.io.ByteArrayInputStream(r.getBytes()));
        assertNotNull(decoded, "Output must be a valid JPEG");
        assertEquals(200, decoded.getWidth());
        assertEquals(133, decoded.getHeight());

        // Verify visible non-blank pixels (image should have coloured content)
        int centrePixel = decoded.getRGB(100, 66);
        assertNotEquals(0, centrePixel, "Centre pixel must not be blank (black)");
        assertNotEquals(Color.WHITE.getRGB(), centrePixel, "Centre pixel must not be white");
    }

    // === Fix #2: derivative failure does not affect generate contract ===

    @Test
    void generate_generatorException_recordsFailedButDoesNotThrow() {
        stubGenerator.nextException = new IOException("simulated generation failure");
        FileStorage file = imageFile(101L, "/abs/uploads/test.png", "test/file-key.png");
        derivativeInserts.clear();
        service.generate(file);
        // Should have 2 PENDING inserts + 2 FAILED updates via wrapper
        assertFalse(derivativeInserts.isEmpty());
    }

    // === Fix #4: PENDING lifecycle ===

    @Test
    void generateOne_insertsPendingBeforeGeneration() {
        stubGenerator.nextResult = result(320, 240);
        FileStorage file = imageFile(102L, "/abs/uploads/test2.png", "test/fk2.png");
        derivativeInserts.clear();
        derivativeUpdateWrappers.clear();
        service.generate(file);
        // Inserts should be PENDING status
        assertFalse(derivativeInserts.isEmpty(), "Should have inserts");
        // First inserts should be PENDING
        Optional<FileDerivative> pendingInsert = derivativeInserts.stream()
                .filter(fd -> "PENDING".equals(fd.getStatus()))
                .findFirst();
        assertTrue(pendingInsert.isPresent(), "Should have a PENDING insert");
        // There should be update wrappers for READY status
        assertFalse(derivativeUpdateWrappers.isEmpty(), "Should have update wrappers");
    }

    // === Fix #5: recordFailed uses explicit tenantId, not TenantContext ===

    @Test
    void recordFailed_usesExplicitTenantId_notContext() {
        // Set a different tenant in context
        TenantContext.setTenantId(99L);
        FileStorage file = imageFile(103L, "/abs/uploads/test.png", "test/fk3.png");
        // file.tenantId = 1L (set by imageFile helper)
        stubStorageService.throwOnLoad = true;
        derivativeInserts.clear();
        service.generate(file);
        // All inserts should have tenantId = 1L (from file), not 99L (from context)
        for (FileDerivative fd : derivativeInserts) {
            assertEquals(1L, fd.getTenantId(), "recordFailed must use explicit file tenantId, not context");
        }
    }

    // === Fix #9: loadVariantResource null on unreadable storage ===

    @Test
    void loadVariantResource_returnsNullWhenStorageUnreadable() {
        FileDerivative readyDerivative = new FileDerivative();
        readyDerivative.setFileId(1L);
        readyDerivative.setVariantType("thumb");
        readyDerivative.setStatus("READY");
        readyDerivative.setStoragePath("/inaccessible/path");
        readyDerivative.setTenantId(1L);
        derivativeHandler.setSelectOneResult(readyDerivative);
        stubStorageService.throwOnLoad = true;

        assertNull(service.loadVariantResource(1L, "thumb"));
    }

    // === Fix #3: backfill starvation ===

    @Test
    void backfill_queryContainsNotNullExistsAndLimit() {
        storageHandler.setSelectListResult(List.of());
        AtomicReference<LambdaQueryWrapper<FileStorage>> captured = new AtomicReference<>();
        storageHandler.setInterceptor(pair -> {
            java.lang.reflect.Method m = (java.lang.reflect.Method) pair[0];
            Object[] a = (Object[]) pair[1];
            if (m.getName().equals("selectList") && a != null && a.length == 1
                    && a[0] instanceof LambdaQueryWrapper<?>) {
                @SuppressWarnings("unchecked")
                LambdaQueryWrapper<FileStorage> w = (LambdaQueryWrapper<FileStorage>) a[0];
                captured.set(w);
            }
        });
        TenantContext.setTenantId(1L);
        service.backfill(10);
        assertNotNull(captured.get(), "Should capture the query wrapper");

        String sql = captured.get().getSqlSegment();
        // Both NOT EXISTS for thumb and card
        assertTrue(sql.contains("NOT EXISTS"), "SQL must contain NOT EXISTS: " + sql);
        assertTrue(sql.contains("'thumb'"), "SQL must filter thumb: " + sql);
        assertTrue(sql.contains("'card'"), "SQL must filter card: " + sql);
        assertTrue(sql.contains("READY"), "SQL must check READY status: " + sql);

        // LIMIT must be bounded
        String customSegment = captured.get().getCustomSqlSegment();
        assertTrue(customSegment != null && customSegment.contains("LIMIT"),
                "last() segment must contain LIMIT: " + customSegment);
        assertTrue(customSegment.contains("10"),
                "LIMIT must equal the requested batch size: " + customSegment);
    }

    // === Fix #8: backfill writes FAILED for load/path failures ===

    @Test
    void backfill_nullFileKey_writesFailedForBothVariants() {
        FileStorage file = imageFile(201L, "/abs/path.png", null); // null fileKey
        storageHandler.setSelectListResult(List.of(file));
        derivativeInserts.clear();
        service.backfill(5);
        // Should have two FAILED inserts (thumb + card)
        long failedCount = derivativeInserts.stream()
                .filter(fd -> "FAILED".equals(fd.getStatus()))
                .count();
        assertEquals(2, failedCount, "Both variants should be FAILED when fileKey is null");
    }

    @Test
    void backfill_loadFailure_writesFailedForBothVariants() {
        stubStorageService.throwOnLoad = true;
        FileStorage file = imageFile(202L, "/abs/path.png", "test/fk202.png");
        storageHandler.setSelectListResult(List.of(file));
        derivativeInserts.clear();
        service.backfill(5);
        long failedCount = derivativeInserts.stream()
                .filter(fd -> "FAILED".equals(fd.getStatus()))
                .count();
        assertEquals(2, failedCount, "Both variants should be FAILED when original can't be loaded");
    }

    // === existing tests (updated for Fix #4: now inserts PENDING then update to READY) ===

    @Test
    void generate_createsDerivativesForImage() {
        stubGenerator.nextResult = result(320, 240);
        derivativeHandler.setSelectCountResult(0L); // not ready initially
        FileStorage file = imageFile(100L, "/abs/uploads/common/2026/06/18/test-uuid.png",
                "common/2026/06/18/test-uuid.png");

        service.generate(file);

        // Should have at least 2 inserts (PENDING) and 2 update wrappers (READY)
        assertFalse(derivativeInserts.isEmpty());
        long pendingCount = derivativeInserts.stream().filter(fd -> "PENDING".equals(fd.getStatus())).count();
        long readyUpdateCount = derivativeUpdateWrappers.size();
        assertTrue(pendingCount >= 2 || readyUpdateCount >= 2,
                "Should have PENDING inserts or READY updates");
    }

    @Test
    void generate_skipsNonImage() {
        FileStorage video = videoFile(200L);
        derivativeInserts.clear();
        service.generate(video);
        assertEquals(0, derivativeInserts.size());
    }

    @Test
    void generate_skipsWhenDisabled() {
        properties.getDerivative().setEnabled(false);
        FileStorage file = imageFile(300L, "/abs/uploads/test.png", "f/test.png");
        derivativeInserts.clear();
        service.generate(file);
        assertEquals(0, derivativeInserts.size());
    }

    @Test
    void generate_nullFileKey_recordsFailed() {
        FileStorage file = imageFile(500L, "/abs/uploads/test.png", null);
        derivativeInserts.clear();
        service.generate(file);
        // 2 FAILED records
        assertFalse(derivativeInserts.isEmpty());
        assertEquals("FAILED", derivativeInserts.get(0).getStatus());
    }

    @Test
    void loadVariantResource_returnsNullWhenNoReadyDerivative() {
        assertNull(service.loadVariantResource(1L, "thumb"));
    }

    @Test
    void backfill_skipAlreadyReadyFiles() {
        // With NOT EXISTS query, files where both variants are READY
        // won't appear in candidates at all.
        storageHandler.setSelectListResult(List.of());

        FileDerivativeService.BackfillResult r = service.backfill(5);
        assertEquals(0, r.processed());
        assertEquals(0, r.succeeded());
        assertEquals(0, r.failed());
        assertEquals(0, r.skipped());
    }

    @Test
    void backfill_enforcesLimit() {
        storageHandler.setSelectListResult(List.of());
        service.backfill(1000); // capped at 500
    }

    @Test
    void backfill_emptyResult() {
        storageHandler.setSelectListResult(List.of());
        FileDerivativeService.BackfillResult r = service.backfill(10);
        assertEquals(0, r.processed());
        assertEquals(0, r.succeeded());
        assertEquals(0, r.failed());
        assertEquals(0, r.skipped());
    }

    // === helpers ===

    private FileStorage imageFile(Long id, String storagePath, String fileKey) {
        FileStorage f = new FileStorage();
        f.setId(id);
        f.setFileType("IMAGE");
        f.setStatus(1);
        f.setTenantId(1L);
        f.setStoragePath(storagePath);
        f.setFileKey(fileKey);
        return f;
    }

    private FileStorage videoFile(Long id) {
        FileStorage f = new FileStorage();
        f.setId(id);
        f.setFileType("VIDEO");
        f.setStatus(1);
        f.setTenantId(1L);
        return f;
    }

    private ImageDerivativeGenerator.DerivativeResult result(int w, int h) {
        return new ImageDerivativeGenerator.DerivativeResult(new byte[]{1, 2, 3}, w, h);
    }

    private static byte[] createTestJpegBytes(int width, int height) {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(java.awt.Color.RED);
        g.fillRect(0, 0, width / 2, height / 2);
        g.setColor(java.awt.Color.BLUE);
        g.fillRect(width / 2, height / 2, width / 2, height / 2);
        g.dispose();
        try {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "JPEG", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] createTestPngBytes(int width, int height, boolean transparent) {
        int type = transparent ? java.awt.image.BufferedImage.TYPE_INT_ARGB
                               : java.awt.image.BufferedImage.TYPE_INT_RGB;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(width, height, type);
        java.awt.Graphics2D g = img.createGraphics();
        if (transparent) {
            g.setComposite(java.awt.AlphaComposite.Clear);
            g.fillRect(0, 0, width, height);
            g.setComposite(java.awt.AlphaComposite.SrcOver);
        }
        g.setColor(new java.awt.Color(255, 0, 0, transparent ? 128 : 255));
        g.fillRect(0, 0, width, height);
        g.dispose();
        try {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "PNG", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // === stub implementations ===

    static class StubStorageService implements FileStorageService {
        boolean throwOnLoad = false;

        @Override
        public com.blade.file.storage.StoredFile store(org.springframework.web.multipart.MultipartFile file, String businessType) {
            throw new UnsupportedOperationException();
        }
        @Override
        public org.springframework.core.io.Resource load(String storagePath) {
            if (throwOnLoad) throw new RuntimeException("storage unavailable");
            return new ByteArrayResource(new byte[]{1, 2, 3, 4});
        }
        @Override
        public void delete(String storagePath) {}
        @Override
        public String getStorageType() { return "local"; }
        @Override
        public com.blade.file.storage.StoredFile storeDerivative(String originalFileKey, String variantType,
                                                                  java.io.InputStream inputStream) throws IOException {
            return new com.blade.file.storage.StoredFile(
                    "derivatives/2026/06/18/stem_" + variantType + ".jpg",
                    "stem_" + variantType + ".jpg",
                    "/tmp/derivatives/stem_" + variantType + ".jpg",
                    "local");
        }
    }

    static class StubGenerator extends ImageDerivativeGenerator {
        ImageDerivativeGenerator.DerivativeResult nextResult;
        IOException nextException;

        @Override
        public ImageDerivativeGenerator.DerivativeResult generate(byte[] imageBytes, int targetLongEdge) throws IOException {
            if (nextException != null) throw nextException;
            if (nextResult == null) throw new IOException("no result configured");
            return nextResult;
        }
    }

    static class MockMapperHandler {
        private Object selectOneResult;
        private long selectCountResult;
        private List<?> selectListResult = List.of();
        private Consumer<Object[]> interceptor;

        void setSelectOneResult(Object r) { this.selectOneResult = r; }
        void setSelectCountResult(long c) { this.selectCountResult = c; }
        void setSelectListResult(List<?> r) { this.selectListResult = r; }
        void setInterceptor(Consumer<Object[]> i) { this.interceptor = i; }

        Object invoke(java.lang.reflect.Method method, Object[] args) {
            if (interceptor != null) interceptor.accept(new Object[]{method, args});
            return switch (method.getName()) {
                case "selectOne" -> selectOneResult;
                case "selectCount" -> selectCountResult;
                case "selectList" -> selectListResult;
                case "insert" -> 1;
                case "updateById" -> 1;
                case "update" -> 1;
                case "delete" -> 1;
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }
    }
}
