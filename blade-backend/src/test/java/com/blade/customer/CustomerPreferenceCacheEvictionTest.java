package com.blade.customer;

import com.blade.common.tenant.TenantContext;
import com.blade.customer.dto.CustomerPreferenceQueryDTO;
import com.blade.customer.service.CustomerService;
import com.blade.customer.service.CustomerStatsCacheService;
import com.blade.order.service.OrderActionService;
import com.blade.system.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0-1 审计整改：偏好缓存键必须收敛到 {@code customer:preference:{customerId}:*}，
 * 使 {@link CustomerStatsCacheService#evictPreferenceCache(Long)} 能一次失效
 * 该客户所有范围/所有时间窗的缓存。
 *
 * <p>真实 Redis（非 mock）：</p>
 * <ul>
 *   <li>同一客户两个不同时间窗 + 另一客户一个键；evict 事务客户后前者消失、后者保留；</li>
 *   <li>订单动作链路：真实 {@code getPreference} 写入真实键，真实 {@code cancelOrder}
 *       落库后该键被清除。</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CustomerPreferenceCacheEvictionTest {

    @Autowired private RedisTemplate<String, Object> redisTemplate;
    @Autowired private CustomerStatsCacheService cacheService;
    @Autowired private CustomerService customerService;
    @Autowired private OrderActionService orderActionService;
    @Autowired private JdbcTemplate jdbc;

    private static final long CACHE_ONLY_CUSTOMER_A = 987650001L;
    private static final long CACHE_ONLY_CUSTOMER_B = 987650002L;

    private Long chainCustomerId;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        deleteKeysFor(CACHE_ONLY_CUSTOMER_A);
        deleteKeysFor(CACHE_ONLY_CUSTOMER_B);
        if (chainCustomerId != null) {
            deleteKeysFor(chainCustomerId);
        }
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void evictRemovesAllRangeKeysForCustomerAndKeepsOtherCustomer() {
        String keyA1 = key(CACHE_ONLY_CUSTOMER_A, "fpA", "2026-01-01", "2026-01-31");
        String keyA2 = key(CACHE_ONLY_CUSTOMER_A, "fpA", "2026-02-01", "2026-02-28");
        String keyB = key(CACHE_ONLY_CUSTOMER_B, "fpB", "2026-01-01", "2026-01-31");
        redisTemplate.opsForValue().set(keyA1, "v1");
        redisTemplate.opsForValue().set(keyA2, "v2");
        redisTemplate.opsForValue().set(keyB, "v3");

        cacheService.evictPreferenceCache(CACHE_ONLY_CUSTOMER_A);

        assertFalse(redisTemplate.hasKey(keyA1), "同一客户第一个时间窗缓存应被失效");
        assertFalse(redisTemplate.hasKey(keyA2), "同一客户第二个时间窗缓存应被失效");
        assertTrue(redisTemplate.hasKey(keyB), "其他客户缓存不得被误删");
    }

    @Test
    void orderActionEvictsRealPreferenceKeyForOrderCustomer() {
        String suffix = Long.toString(System.nanoTime()).substring(8);
        chainCustomerId = customer("缓存联动客户" + suffix);
        Long outletId = outlet("CE-" + suffix);
        Long userId = user("ce_" + suffix);
        bind(userId, outletId);
        authenticate(userId);
        Long orderId = order(outletId, chainCustomerId, userId);

        // 真实 service 写入真实缓存键（键格式由生产代码决定）
        CustomerPreferenceQueryDTO dto = new CustomerPreferenceQueryDTO();
        customerService.getPreference(chainCustomerId, dto);
        Set<String> before = keysFor(chainCustomerId);
        assertFalse(before.isEmpty(), "getPreference 应写入 customer:preference:{customerId}:* 真实缓存键");

        orderActionService.cancelOrder(orderId, "缓存联动测试", "TEST");

        assertTrue(keysFor(chainCustomerId).isEmpty(),
                "取消订单落库后应失效该订单客户的全部偏好缓存键，实际残留：" + keysFor(chainCustomerId));
    }

    @Test
    void preferenceCountsPurchasedQuantityInsteadOfSkuRows() {
        String suffix = Long.toString(System.nanoTime()).substring(8);
        chainCustomerId = customer("数量口径客户" + suffix);
        Long outletId = outlet("QTY-" + suffix);
        Long userId = user("qty_" + suffix);
        bind(userId, outletId);
        authenticate(userId);
        Long orderId = order(outletId, chainCustomerId, userId);
        jdbc.update("UPDATE sale_order SET fulfillment_status='COMPLETED', status=4 WHERE id=?", orderId);
        preferenceItem(orderId, "连衣裙", "黑色", "M", 24);
        preferenceItem(orderId, "连衣裙", "白色", "L", 6);

        CustomerPreferenceQueryDTO dto = new CustomerPreferenceQueryDTO();
        var preference = customerService.getPreference(chainCustomerId, dto);

        assertEquals(30, preference.getCategories().get(0).getCount(), "商品统计必须累计购买件数，而不是 SKU 行数");
        assertEquals(24, preference.getColors().stream()
                .filter(item -> "黑色".equals(item.getColorName())).findFirst().orElseThrow().getCount());
        assertEquals(6, preference.getSizes().stream()
                .filter(item -> "L".equals(item.getSizeName())).findFirst().orElseThrow().getCount());
    }

    // ==================== fixtures ====================

    private String key(Long customerId, String fingerprint, String start, String end) {
        return CustomerStatsCacheService.PREFERENCE_KEY_PREFIX + customerId + ":"
                + fingerprint + ":" + start + ":" + end;
    }

    private Set<String> keysFor(Long customerId) {
        Set<String> keys = redisTemplate.keys(CustomerStatsCacheService.PREFERENCE_KEY_PREFIX + customerId + ":*");
        return keys == null ? Set.of() : keys;
    }

    private void deleteKeysFor(Long customerId) {
        Set<String> keys = keysFor(customerId);
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private void authenticate(Long actorId) {
        User principal = new User();
        principal.setId(actorId);
        principal.setUsername("cache_evict_" + actorId);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(
                        new SimpleGrantedAuthority("data:outlet:all"),
                        new SimpleGrantedAuthority("data:order:peopleAll"),
                        new SimpleGrantedAuthority("btn:order:cancel"))));
    }

    private Long outlet(String code) {
        jdbc.update("INSERT INTO sales_outlet(tenant_id,outlet_code,outlet_name,outlet_type,status,deleted,sort,is_tenant_default)"
                + " VALUES(1,?,?,'STORE',1,0,0,0)", code, "档口" + code);
        return jdbc.queryForObject("SELECT id FROM sales_outlet WHERE tenant_id=1 AND outlet_code=?", Long.class, code);
    }

    private Long customer(String name) {
        jdbc.update("INSERT INTO crm_customer(name,address,remark,tenant_id,deleted) VALUES(?, '地址','备注',1,0)", name);
        return jdbc.queryForObject("SELECT id FROM crm_customer WHERE name=? ORDER BY id DESC LIMIT 1", Long.class, name);
    }

    private Long user(String username) {
        jdbc.update("INSERT INTO sys_user(username,password,nickname,status,tenant_id,deleted) VALUES(?,?,'缓存测试',1,1,0)",
                username, "x");
        return jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, username);
    }

    private void bind(Long actorId, Long outletId) {
        jdbc.update("INSERT INTO sys_user_outlet(tenant_id,user_id,outlet_id,is_default,status,deleted) "
                + "VALUES(1,?,?,1,1,0)", actorId, outletId);
    }

    private Long order(Long outletId, Long customerId, Long salesmanId) {
        String orderNo = "CE-" + Long.toString(System.nanoTime()).substring(8);
        jdbc.update("INSERT INTO sale_order(order_no,order_date,order_type,customer_id,customer_name,total_amount,paid_amount,"
                        + "payment_status,status,fulfillment_status,fulfillment_mode,collection_status,"
                        + "gross_received_amount,cash_refund_amount,sales_return_amount,net_received_amount,balance_amount,"
                        + "is_delivered,salesman_id,source_outlet_id,tenant_id,deleted,version) "
                        + "VALUES(?,CURDATE(),'SPOT',?,?,100,0,0,3,'CONFIRMED','UNDECIDED','UNPAID',0,0,0,0,100,0,?,?,1,0,0)",
                orderNo, customerId, "缓存联动客户", salesmanId, outletId);
        return jdbc.queryForObject("SELECT id FROM sale_order WHERE order_no=?", Long.class, orderNo);
    }

    private void preferenceItem(Long orderId, String productName, String color, String size, int quantity) {
        jdbc.update("INSERT INTO sale_order_item(tenant_id,order_id,product_id,product_name,color_name,size_name,price,quantity,subtotal) "
                        + "VALUES(1,?,1,?,?,?,10,?,?)",
                orderId, productName, color, size, quantity, quantity * 10);
    }
}
